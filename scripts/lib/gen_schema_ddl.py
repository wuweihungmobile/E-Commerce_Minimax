# -*- coding: utf-8 -*-
"""以「乾淨 DB + 全部 Flyway 遷移」為權威來源產生可讀 DDL（DEF-062 / Sprint 111）。

由 scripts/validate-schema-doc.sh 呼叫。容器名以 SCHEMA_DOC_PG_CONTAINER 環境變數指定。
🔴 這支程式的輸出「就是」SRD_Database_Schema.md §2 的內容——不要手改文件裡的 DDL，
   要改請改 Flyway 遷移，再重跑 make sync-schema-doc。"""
import os, subprocess, sys, re

CONTAINER = os.environ.get('SCHEMA_DOC_PG_CONTAINER', 'schemadoc-pg')

SEP = '@@@'

def q(sql):
    r = subprocess.run(['docker','exec',CONTAINER,'psql','-U','koala','-d','nextkeytest','-tA','-F',SEP,'-c',sql],
                       capture_output=True, text=True)
    if r.returncode != 0:
        sys.exit('psql 失敗: ' + r.stderr)
    return [l.split(SEP) for l in r.stdout.strip().split('\n') if l.strip()]

def tidy(s):
    """移除 PostgreSQL 回顯的隱式轉型雜訊並簡化 IN 清單，不改變語意。
    等價性由 scripts/validate-schema-doc.sh 以「文件 DDL 建庫後與 Flyway 建庫逐欄比對」證明。"""
    s = s.replace('::character varying[]', '').replace('::text[]', '')
    s = s.replace('::character varying', '').replace('::text', '').replace('::bpchar', '')
    s = s.replace('character varying', 'VARCHAR')
    m = re.search(r"\(+\s*\(?(\w+)\)?\s*=\s*ANY\s*\(+\s*ARRAY\[(.+?)\]\s*\)+", s)
    if m and s.strip().upper().startswith('CHECK'):
        vals = ', '.join(v.strip() for v in m.group(2).split(','))
        return "CHECK (%s IN (%s))" % (m.group(1), vals)
    return s

def gen(table, indent='    '):
    cols = q("""SELECT a.attname, format_type(a.atttypid,a.atttypmod),
                 CASE WHEN a.attnotnull THEN 'Y' ELSE '-' END,
                 COALESCE(pg_get_expr(d.adbin,d.adrelid),'-'),
                 CASE WHEN a.attgenerated='' THEN '-' ELSE a.attgenerated END
                FROM pg_attribute a
                LEFT JOIN pg_attrdef d ON d.adrelid=a.attrelid AND d.adnum=a.attnum
                WHERE a.attrelid='%s'::regclass AND a.attnum>0 AND NOT a.attisdropped
                ORDER BY a.attnum;""" % table)
    lines = []
    for name, typ, notnull, default, generated in cols:
        typ = typ.replace('character varying', 'VARCHAR').replace('timestamp with time zone', 'TIMESTAMP WITH TIME ZONE')
        typ = re.sub(r'^(uuid|text|jsonb|boolean|integer|bigint|numeric|date|smallint|real|double precision)',
                     lambda m: m.group(1).upper(), typ)
        s = "%s%s %s" % (indent, name, typ)
        if generated == 's':
            s += " GENERATED ALWAYS AS (%s) STORED" % tidy(default)
        else:
            if notnull == 'Y':
                s += " NOT NULL"
            if default != '-':
                s += " DEFAULT %s" % tidy(default)
        lines.append(s)
    # contype 'n'（PG17+ 具名 NOT NULL 約束）為欄位層 NOT NULL 的重複表述，排除
    cons = q("""SELECT conname, pg_get_constraintdef(oid), contype
                FROM pg_constraint WHERE conrelid='%s'::regclass AND contype <> 'n'
                ORDER BY CASE contype WHEN 'p' THEN 1 WHEN 'u' THEN 2 WHEN 'f' THEN 3 ELSE 4 END, conname;""" % table)
    body = ",\n".join(lines)
    if cons:
        body += ",\n\n%s-- 約束\n" % indent + ",\n".join(
            "%sCONSTRAINT %s %s" % (indent, n, tidy(d)) for n, d, t in cons)
    ddl = "CREATE TABLE %s (\n%s\n);" % (table, body)
    idx = q("""SELECT indexdef FROM pg_indexes
               WHERE schemaname='public' AND tablename='%s'
                 AND indexname NOT IN (SELECT conname FROM pg_constraint WHERE conrelid='%s'::regclass)
               ORDER BY indexname;""" % (table, table))
    if idx:
        defs = [d[0].replace('public.', '').replace(' USING btree', '') + ';' for d in idx]
        ddl += "\n\n-- 索引\n" + "\n".join(defs)
    return ddl

if __name__ == '__main__':
    print(gen(sys.argv[1]))
