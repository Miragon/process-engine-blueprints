package io.miragon.common.architecture;

import org.junit.jupiter.api.Nested;

/**
 * A single, ready-to-use <b>ArchUnit</b> architecture suite for a service. It owns every rule that can be
 * checked against compiled bytecode; the source-structure rules are enforced by <b>Checkstyle</b> in the
 * build instead.
 *
 * <p>A service wires up the full suite with one line:
 *
 * <pre>{@code
 * class ArchitectureTest extends ServiceArchitectureTest { ArchitectureTest() { super("io.miragon.blueprint"); } }
 * }</pre>
 *
 * <h2>Who owns what</h2>
 *
 * <ul>
 *   <li><b>ArchUnit</b> (this suite) reads compiled <b>bytecode</b>, so it sees the fully resolved dependency
 *       graph. It owns the <em>dependency &amp; structure</em> rules — hexagonal layering,
 *       technology-neutrality of domain &amp; application, port/adapter isolation ({@link Dependencies}),
 *       naming conventions ({@link Naming}), freedom of cycles and the no-{@code println} check
 *       ({@link CodingGuidelines}).</li>
 *   <li><b>Checkstyle</b> reads Java <b>source</b>, so it sees what the bytecode no longer carries, such as
 *       import statements. It owns the <em>source-structure</em> rules — one top-level type per file (SRP)
 *       and no wildcard imports (except {@code java.util}). They live in
 *       {@code config/checkstyle/source-guidelines.xml} and run in the {@code validate} phase of the Maven
 *       build for every module, test sources included (see {@code docs/adr/0007}).</li>
 * </ul>
 *
 * <p>This module is <b>self-contained</b>: it carries the ArchUnit dependency and its own copies of the
 * rules, so it can be dropped into a service as a single test dependency.
 */
public abstract class ServiceArchitectureTest {

    private final String rootPackage;

    protected ServiceArchitectureTest(String rootPackage) {
        this.rootPackage = rootPackage;
    }

    @Nested
    class Dependencies extends HexagonalArchitectureTest {

        Dependencies() {
            super(ServiceArchitectureTest.this.rootPackage);
        }
    }

    @Nested
    class Naming extends NamingConventionArchitectureTest {

        Naming() {
            super(ServiceArchitectureTest.this.rootPackage);
        }
    }

    @Nested
    class CodingGuidelines extends BasicCodingGuidelinesTest {

        CodingGuidelines() {
            super(ServiceArchitectureTest.this.rootPackage);
        }
    }
}
