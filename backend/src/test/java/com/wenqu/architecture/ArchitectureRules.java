package com.wenqu.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.CompositeArchRule;
import java.util.List;
import java.util.stream.Stream;

/**
 * 架构规则，对应 docs/architecture.md「架构规则的自动检查」。
 *
 * <p>根包和模块列表作为参数，使规则既能检查真实代码，也能在自检中检查故意违规的样例代码。
 */
final class ArchitectureRules {

    private final String root;
    private final List<String> businessModules;
    private final List<String> modulesWithData;

    ArchitectureRules(String root, List<String> businessModules) {
        this.root = root;
        this.businessModules = businessModules;
        this.modulesWithData =
                Stream.concat(businessModules.stream(), Stream.of("infra")).toList();
    }

    /** 跨模块只能访问对方的 api 包；infra、common 不依赖业务模块。 */
    ArchRule moduleBoundaries() {
        Stream<ArchRule> crossModule = modulesWithData.stream()
                .map(module -> noClasses()
                        .that()
                        .resideOutsideOfPackage(pkg(module) + "..")
                        .should()
                        .dependOnClassesThat()
                        .resideInAnyPackage(internalPackages(module))
                        .because("其他模块只能访问 " + module + " 的 api 包"));
        Stream<ArchRule> foundations = Stream.of(
                noClasses()
                        .that()
                        .resideInAnyPackage(pkg("infra") + "..", pkg("common") + "..")
                        .should()
                        .dependOnClassesThat()
                        .resideInAnyPackage(businessModules.stream()
                                .map(module -> pkg(module) + "..")
                                .toArray(String[]::new))
                        .because("infra 和 common 不依赖业务模块"),
                noClasses()
                        .that()
                        .resideInAPackage(pkg("common") + "..")
                        .should()
                        .dependOnClassesThat()
                        .resideInAPackage(pkg("infra") + "..")
                        .because("common 不依赖任何模块"));
        return all(Stream.concat(crossModule, foundations));
    }

    /** controller → service → repository，不允许跨层或反向调用。 */
    ArchRule layering() {
        return layeredArchitecture()
                .consideringOnlyDependenciesInLayers()
                .withOptionalLayers(true)
                .layer("Controller")
                .definedBy(root + "..controller..")
                .layer("Service")
                .definedBy(root + "..service..")
                .layer("Repository")
                .definedBy(root + "..repository..")
                .whereLayer("Controller")
                .mayNotBeAccessedByAnyLayer()
                .whereLayer("Service")
                .mayOnlyBeAccessedByLayers("Controller")
                .whereLayer("Repository")
                .mayOnlyBeAccessedByLayers("Service");
    }

    /** 顶层模块之间无循环依赖。 */
    ArchRule noCycles() {
        return slices().matching(root + ".(*)..").should().beFreeOfCycles().allowEmptyShould(true);
    }

    /** jOOQ 生成的表类只能由所属模块的 repository 访问。 */
    ArchRule tableOwnership() {
        return all(modulesWithData.stream()
                .map(module -> noClasses()
                        .that()
                        .resideOutsideOfPackages(pkg(module) + "..repository..", pkg("jooq") + "..")
                        .should()
                        .dependOnClassesThat()
                        .resideInAPackage(pkg("jooq") + "." + module + "..")
                        .because(module + " schema 的表只能由 " + module + " 模块的 repository 访问")));
    }

    /** api 包不引用本模块内部类，避免内部类型通过接口泄露。 */
    ArchRule apiDoesNotExposeInternals() {
        return all(modulesWithData.stream()
                .map(module -> noClasses()
                        .that()
                        .resideInAPackage(pkg(module) + "..api..")
                        .should()
                        .dependOnClassesThat()
                        .resideInAnyPackage(internalPackages(module))
                        .because("api 是 " + module + " 对外的契约，不能暴露内部类型")));
    }

    /** domain 不依赖 Spring 和 jOOQ。 */
    ArchRule domainIsFrameworkFree() {
        return noClasses()
                .that()
                .resideInAPackage(root + "..domain..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("org.springframework..", "org.jooq..", pkg("jooq") + "..")
                .because("domain 只包含领域逻辑")
                .allowEmptyShould(true);
    }

    private String pkg(String module) {
        return root + "." + module;
    }

    private String[] internalPackages(String module) {
        return Stream.of("controller", "service", "repository", "domain")
                .map(layer -> pkg(module) + ".." + layer + "..")
                .toArray(String[]::new);
    }

    private static ArchRule all(Stream<ArchRule> rules) {
        return CompositeArchRule.of(
                rules.map(rule -> rule.allowEmptyShould(true)).toList());
    }
}
