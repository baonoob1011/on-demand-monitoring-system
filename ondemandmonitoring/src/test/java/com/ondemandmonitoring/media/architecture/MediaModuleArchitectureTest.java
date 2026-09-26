package com.ondemandmonitoring.media.architecture;

import com.ondemandmonitoring.common.entity.BaseEntity;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import jakarta.persistence.Entity;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.ondemandmonitoring", importOptions = ImportOption.DoNotIncludeTests.class)
class MediaModuleArchitectureTest {
    @ArchTest
    static final ArchRule MEDIA_MUST_NOT_ACCESS_OTHER_MODULE_REPOSITORIES =
            noClasses().that().resideInAPackage("..media..")
                    .should().dependOnClassesThat(new DescribedPredicate<>("repositories owned by other modules") {
                        @Override
                        public boolean test(JavaClass type) {
                            return type.getPackageName().startsWith("com.ondemandmonitoring.")
                                    && type.getPackageName().contains(".repository")
                                    && !type.getPackageName().startsWith("com.ondemandmonitoring.media.repository");
                        }
                    });

    @ArchTest
    static final ArchRule MEDIA_MUST_USE_OTHER_MODULE_SERVICE_CONTRACTS =
            noClasses().that().resideInAPackage("..media..")
                    .should().dependOnClassesThat(new DescribedPredicate<>("service implementations owned by other modules") {
                        @Override
                        public boolean test(JavaClass type) {
                            return type.getPackageName().startsWith("com.ondemandmonitoring.")
                                    && type.getPackageName().contains(".service.impl")
                                    && !type.getPackageName().startsWith("com.ondemandmonitoring.media.");
                        }
                    });

    @ArchTest
    static final ArchRule CONTROLLERS_MUST_NOT_ACCESS_REPOSITORIES =
            noClasses().that().resideInAPackage("..media.controller..")
                    .should().dependOnClassesThat().resideInAPackage("..repository..");

    @ArchTest
    static final ArchRule MEDIA_ENTITIES_EXTEND_BASE_ENTITY =
            classes().that().resideInAPackage("..media.domain..")
                    .and().areAnnotatedWith(Entity.class).should().beAssignableTo(BaseEntity.class);
}
