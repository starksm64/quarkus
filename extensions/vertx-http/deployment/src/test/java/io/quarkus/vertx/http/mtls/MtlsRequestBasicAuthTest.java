package io.quarkus.vertx.http.mtls;

import static org.hamcrest.Matchers.is;

import java.io.File;
import java.net.URL;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.security.test.utils.TestIdentityController;
import io.quarkus.security.test.utils.TestIdentityProvider;
import io.quarkus.test.QuarkusUnitTest;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.vertx.http.runtime.security.QuarkusHttpUser;
import io.quarkus.vertx.http.security.TestTrustedIdentityProvider;
import io.restassured.RestAssured;
import io.smallrye.certs.Format;
import io.smallrye.certs.junit5.Certificate;
import io.smallrye.certs.junit5.Certificates;
import io.vertx.ext.web.Router;

@Certificates(baseDir = "target/certs", certificates = @Certificate(name = "mtls-test", password = "secret", formats = {
        Format.JKS, Format.PKCS12, Format.PEM }, client = true))
public class MtlsRequestBasicAuthTest {

    private static final String configuration = """
            quarkus.http.ssl.certificate.key-store-file=server-keystore.jks
            quarkus.http.ssl.certificate.key-store-password=secret
            quarkus.http.ssl.certificate.trust-store-file=server-truststore.jks
            quarkus.http.ssl.certificate.trust-store-password=secret
            quarkus.http.ssl.client-auth=REQUEST
            quarkus.http.auth.basic=true
            quarkus.http.auth.proactive=true
            """;

    @TestHTTPResource(value = "/mtls", ssl = true)
    URL url;

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .withApplicationRoot((jar) -> jar
                    .addClasses(MyBean.class)
                    .addClasses(TestIdentityProvider.class, TestTrustedIdentityProvider.class, TestIdentityController.class)
                    .addAsResource(new StringAsset(configuration), "application.properties")
                    .addAsResource(new File("target/certs/mtls-test-keystore.jks"), "server-keystore.jks")
                    .addAsResource(new File("target/certs/mtls-test-server-truststore.jks"), "server-truststore.jks"));

    @BeforeAll
    public static void setup() {
        TestIdentityController.resetRoles()
                .add("admin", "admin", "admin");
    }

    @Test
    public void testClientAuthentication() {
        RestAssured.given()
                .keyStore("target/certs/mtls-test-client-keystore.jks", "secret")
                .trustStore("target/certs/mtls-test-client-truststore.jks", "secret")
                .get(url).then().statusCode(200).body(is("CN=localhost"));
    }

    @Test
    public void testNoClientCert() {
        RestAssured.given()
                .trustStore("target/certs/mtls-test-client-truststore.jks", "secret")
                .get(url).then().statusCode(200).body(is(""));
    }

    @Test
    public void testNoClientCertBasicAuth() {
        RestAssured.given()
                .auth()
                .preemptive()
                .basic("admin", "admin")
                .trustStore("target/certs/mtls-test-client-truststore.jks", "secret")
                .get(url).then().statusCode(200).body(is("admin"));
    }

    @ApplicationScoped
    static class MyBean {

        public void register(@Observes Router router) {
            router.get("/mtls").handler(rc -> {
                rc.response().end(QuarkusHttpUser.class.cast(rc.user()).getSecurityIdentity().getPrincipal().getName());
            });
        }

    }
}
