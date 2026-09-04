# -*- coding: utf-8 -*-
"""比對 SRD_Database_Schema.md §2 的 DDL 與「Flyway 建成之實際 schema」（DEF-062 / Sprint 111）。

用法：
    python3 scripts/lib/check_schema_doc.py            # 檢查，不一致則 exit 1
    python3 scripts/lib/check_schema_doc.py --write    # 直接以實際 schema 覆寫文件

為什麼需要這支：SRD_Database_Schema.md 曾經整整 100 個 Sprint 沒人發現它逐欄都是錯的
（16 張表全漂移、117 個幽靈欄位、2 張表從未實作）。文件債沒有守門就必定重演。
"""
import io, re, sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_schema_ddl import gen, q

DOC = 'docs/02_architecture/SRD_Database_Schema.md'
PRD = 'docs/01_requirements/E-Commerce_PRD_v1.0_Final.md'
PHANTOM = ('user_profiles', 'pricing_overrides')
# 章節之間不可跨越：中段用 (?:(?!\n#{2,4} ).)*? 擋住，否則沒有 DDL 的章節（未實作表）
# 會一路吃到下一張表的區塊，產生假警報。
PAT = re.compile(r'(#### 2\.\d+\.\d+ `([a-z_]+)` - [^\n]*\n)((?:(?!\n#{2,4} ).)*?)```sql\n((?:(?!\n#{2,4} ).)*?)\n```', re.S)
# §2.6 已知範圍外資料表清單（DEF-068）：一行一張表，格式 `| \`table_name\` | 說明 |`
OUT_OF_SCOPE_PAT = re.compile(r'### 2\.6 已知範圍外資料表.*?\n(.*?)(?=\n## )', re.S)
PRD_TABLE_PAT = re.compile(r'\n#### 8\.2\.[0-9A-Za-z\-]+ ([a-z_]+)[^\n]*\n')


def check_prd():
    """PRD §8.2 的欄位清單是 markdown 表格（非 DDL），只比對欄位名集合。"""
    doc = io.open(PRD, encoding='utf-8', newline='').read().replace('\r\n', '\n')
    secs = re.findall(r'\n#### 8\.2\.[0-9A-Za-z\-]+ ([a-z_]+)[^\n]*\n(.*?)(?=\n#### |\n### |\n## )',
                      doc, re.S)
    actual_tables = set(r[0] for r in q(
        "SELECT table_name FROM information_schema.tables "
        "WHERE table_schema='public' AND table_type='BASE TABLE'"))
    problems = []
    for tbl, body in secs:
        if tbl not in actual_tables:
            problems.append('PRD §8.2 %s：實作中不存在此表' % tbl)
            continue
        listed = set()
        for line in body.split('\n'):
            cm = re.match(r'\|\s*`?([a-z_]+)`?\s*\|', line)
            if cm:
                listed.add(cm.group(1))
        act = set(r[0] for r in q(
            "SELECT column_name FROM information_schema.columns "
            "WHERE table_schema='public' AND table_name='%s'" % tbl))
        ghost = sorted(listed - act)
        missing = sorted(act - listed)
        if ghost:
            problems.append('PRD §8.2 %s：列了實作沒有的欄位 %s' % (tbl, ', '.join(ghost)))
        if missing:
            problems.append('PRD §8.2 %s：漏列實作欄位 %s' % (tbl, ', '.join(missing)))
    return problems


def out_of_scope_tables(doc):
    """§2.6「已知範圍外資料表」清單（DEF-068）：`| \\`table_name\\` | 說明 |` 逐行列出。"""
    m = OUT_OF_SCOPE_PAT.search(doc)
    if not m:
        return set()
    return set(re.findall(r'\|\s*`([a-z_]+)`\s*\|', m.group(1)))


def check_coverage(doc, srd_tables):
    """DEF-068：實作存在、但 SRD §2 / PRD §8.2 / §2.6 範圍外清單完全沒提到的表，直接報錯——
    避免重演「文件沒寫的表，漂移完全不會被發現」（SRD 曾 100 個 Sprint 沒人發現逐欄都是錯的）。"""
    prd_doc = io.open(PRD, encoding='utf-8', newline='').read().replace('\r\n', '\n')
    prd_tables = set(PRD_TABLE_PAT.findall(prd_doc))
    scope = out_of_scope_tables(doc)
    known = srd_tables | prd_tables | scope | set(PHANTOM)
    actual = set(r[0] for r in q(
        "SELECT table_name FROM information_schema.tables "
        "WHERE table_schema='public' AND table_type='BASE TABLE'"))
    problems = []
    for tbl in sorted(actual - known):
        problems.append('%s：實作存在，但未出現在 SRD §2 / PRD §8.2 / §2.6 範圍外清單任何一處（DEF-068 守門）' % tbl)
    for tbl in sorted(scope - actual):
        problems.append('§2.6 範圍外清單列了 %s，但實作中已不存在，請從清單移除' % tbl)
    return problems


def main():
    write = '--write' in sys.argv
    doc = io.open(DOC, encoding='utf-8').read()
    problems = []
    new_doc = doc
    srd_tables = set()

    for m in PAT.finditer(doc):
        tbl, block = m.group(2), m.group(4)
        srd_tables.add(tbl)
        if tbl in PHANTOM:
            problems.append('%s：不應有 DDL 區塊（此表從未被實作）' % tbl)
            continue
        hdr = re.match(r'(-- =+\n-- Table:[^\n]*\n(?:-- [^\n]*\n)*?-- =+\n)', block)
        prefix = hdr.group(1) if hdr else ''
        expected = prefix + gen(tbl)
        if block.strip() != expected.strip():
            problems.append('%s：文件 DDL 與實際 schema 不一致' % tbl)
            if write:
                new_doc = new_doc.replace(m.group(0),
                                          m.group(1) + m.group(3) + '```sql\n' + expected + '\n```')

    # 文件未收錄、但實作存在的幽靈表檢查（反向）
    for tbl in PHANTOM:
        if re.search(r'CREATE TABLE %s\b' % tbl, doc):
            problems.append('%s：文件仍宣告此表，但實作中不存在' % tbl)

    # PRD §8.2 的欄位清單（--write 不處理 PRD，因其含人工撰寫的中文說明）
    prd_problems = check_prd()
    # DEF-068：文件涵蓋率守門（新表沒進 §2/PRD/§2.6 任何一處就報錯），--write 不處理（需人工分類）
    coverage_problems = check_coverage(doc, srd_tables)

    if write and problems:
        io.open(DOC, 'w', encoding='utf-8').write(new_doc)
        print('✅ 已依實際 schema 更新 %s（%d 張表）' % (DOC, len(problems)))
        if prd_problems or coverage_problems:
            print('⚠️  仍有不一致（--write 不會自動處理，需人工補上說明）：')
            for p in prd_problems + coverage_problems:
                print('   - %s' % p)
            return 1
        return 0

    if problems or prd_problems or coverage_problems:
        print('🔴 文件與實際 schema 不一致：')
        for p in problems + prd_problems + coverage_problems:
            print('   - %s' % p)
        print('')
        print('   修法：改 Flyway 遷移才是改 schema。')
        print('        SRD 文件請以 `make sync-schema-doc` 重新產生，不要手改裡面的 DDL；')
        print('        PRD §8.2 需人工補上欄位與中文說明（自動產生會失去語意）；')
        print('        新表若暫不補正式 DDL，至少把表名列進 SRD §2.6「已知範圍外資料表」清單。')
        return 1

    print('✅ SRD_Database_Schema.md 的 DDL、PRD §8.2 的欄位清單，均與 Flyway 實際 schema 一致（含 §2.6 涵蓋率守門，DEF-068）')
    return 0


if __name__ == '__main__':
    sys.exit(main())
