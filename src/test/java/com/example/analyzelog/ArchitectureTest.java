package com.example.analyzelog;

import com.enofex.taikai.Taikai;
import com.tngtech.archunit.core.domain.JavaModifier;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

class ArchitectureTest {

    @Test
    void shouldFulfillConstraints() {
        Taikai.builder()
                .namespace("com.example.analyzelog")
                .failOnEmpty(false)
                .java(java -> java
                        .noUsageOfDeprecatedAPIs()
                        .noUsageOfSystemOutOrErr()
                        .fieldsShouldNotBePublic()
                        .finalClassesShouldNotHaveProtectedMembers()
                        .methodsShouldNotDeclareGenericExceptions()
                        .utilityClassesShouldBeFinalAndHavePrivateConstructor()
                        .serialVersionUIDFieldsShouldBeStaticFinalLong()
                        .classesShouldImplementHashCodeAndEquals()
                        .imports(imports -> imports
                                .shouldNotImport("..internal.."))
                        .naming(naming -> naming
                                .packagesShouldMatchDefault()
                                .classesShouldNotMatch(".*Impl")
                                .enumConstantsShouldFollowConventions()
                                .interfacesShouldNotHavePrefixI()))
                .logging(logging -> logging
                        .loggersShouldFollowConventions(Logger.class, "log", List.of(JavaModifier.PRIVATE, JavaModifier.FINAL)))
                .test(test -> test
                        .junit(junit -> junit
                                .classesShouldEndWithTest()
                                .classesShouldBePackagePrivate(".*Test")
                                .classesShouldNotBeAnnotatedWithDisabled()
                                .methodsShouldBePackagePrivate()
                                .methodsShouldNotBeAnnotatedWithDisabled()
                                .methodsShouldContainAssertionsOrVerifications()))
                .spring(spring -> spring
                        .noAutowiredFields()
                        .noSelfInvocationOfProxiedMethods()
                        .boot(boot -> boot
                                .applicationClassShouldResideInPackage())
                        .controllers(controllers -> controllers
                                .namesShouldEndWithController()
                                .shouldNotDependOnOtherControllers()
                                .shouldNotDependOnRepositories())
                        .services(services -> services
                                .namesShouldEndWithService()
                                .shouldBeAnnotatedWithService()
                                .shouldNotDependOnControllers())
                        .repositories(repositories -> repositories
                                .namesShouldEndWithRepository()
                                .shouldBeAnnotatedWithRepository()
                                .shouldNotDependOnControllers())
                        .transactional(transactional -> transactional
                                .methodsShouldBePublic()
                                .shouldNotBeSelfInvoked()
                                .shouldNotBeUsedInControllers()))
                .build()
                .checkAll();
    }
}