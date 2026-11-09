package com.sprintstart.sprintstartbackend.quality;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import org.pitest.bytecode.analysis.ClassTree;
import org.pitest.mutationtest.build.InterceptorParameters;
import org.pitest.mutationtest.build.InterceptorType;
import org.pitest.mutationtest.build.MutationInterceptor;
import org.pitest.mutationtest.build.MutationInterceptorFactory;
import org.pitest.mutationtest.engine.Mutater;
import org.pitest.mutationtest.engine.MutationDetails;
import org.pitest.plugin.Feature;

/**
 * Drops mutants that remove calls the Kotlin compiler inserts on its own: the null checks on
 * platform types ({@code kotlin.jvm.internal.Intrinsics}) and the coroutine result unwrapping
 * ({@code kotlin.ResultKt.throwOnFailure}). Removing one changes nothing unless a null or a failed
 * result arrives, so no test can kill it, and counting it would only lower the score.
 */
public final class KotlinGeneratedCodeFilter implements MutationInterceptorFactory {

    static final List<String> GENERATED_CALLS =
            List.of("kotlin/jvm/internal/Intrinsics::", "kotlin/ResultKt::throwOnFailure");

    @Override
    public MutationInterceptor createInterceptor(InterceptorParameters params) {
        return new Interceptor();
    }

    @Override
    public Feature provides() {
        return Feature.named("FKOTLIN_GENERATED")
                .withOnByDefault(true)
                .withDescription(description());
    }

    @Override
    public String description() {
        return "Filter mutants on calls the Kotlin compiler generates";
    }

    static boolean isGenerated(MutationDetails mutation) {
        String description = mutation.getDescription();
        return GENERATED_CALLS.stream().anyMatch(description::contains);
    }

    static final class Interceptor implements MutationInterceptor {

        @Override
        public InterceptorType type() {
            return InterceptorType.FILTER;
        }

        @Override
        public void begin(ClassTree clazz) {}

        @Override
        public Collection<MutationDetails> intercept(
                Collection<MutationDetails> mutations, Mutater mutater) {
            return mutations.stream().filter(m -> !isGenerated(m)).collect(Collectors.toList());
        }

        @Override
        public void end() {}
    }
}
