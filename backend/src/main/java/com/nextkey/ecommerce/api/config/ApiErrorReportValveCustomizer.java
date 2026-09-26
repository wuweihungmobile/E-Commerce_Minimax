package com.nextkey.ecommerce.api.config;

import org.apache.catalina.core.StandardHost;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.stereotype.Component;

/** 把內嵌 Tomcat 的 Host 錯誤頁 valve 換成 {@link ApiErrorReportValve}（Sprint 206，DEF-283）。 */
@Component
public class ApiErrorReportValveCustomizer implements WebServerFactoryCustomizer<TomcatServletWebServerFactory> {

    @Override
    public void customize(final TomcatServletWebServerFactory factory) {
        factory.addContextCustomizers(context -> {
            if (context.getParent() instanceof StandardHost host) {
                host.setErrorReportValveClass(ApiErrorReportValve.class.getName());
            }
        });
    }
}
