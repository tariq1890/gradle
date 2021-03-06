/*
 * Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.platform.base.internal.registry;

import org.apache.commons.lang.StringUtils;
import org.gradle.api.Nullable;
import org.gradle.model.InvalidModelRuleDeclarationException;
import org.gradle.model.internal.core.ExtractedModelRule;
import org.gradle.model.internal.core.ModelReference;
import org.gradle.model.internal.inspect.MethodRuleDefinition;
import org.gradle.model.internal.inspect.ValidationProblemCollector;
import org.gradle.model.internal.manage.schema.ModelSchema;
import org.gradle.model.internal.manage.schema.ModelSchemaStore;
import org.gradle.model.internal.type.ModelType;
import org.gradle.platform.base.InvalidModelException;
import org.gradle.platform.base.internal.builder.TypeBuilderFactory;
import org.gradle.platform.base.internal.builder.TypeBuilderInternal;

import java.lang.annotation.Annotation;

public abstract class TypeModelRuleExtractor<A extends Annotation, T, U extends T> extends AbstractAnnotationDrivenComponentModelRuleExtractor<A> {

    private final String modelName;
    private final ModelType<T> baseInterface;
    private final ModelType<U> baseImplementation;
    private final ModelType<?> builderInterface;
    private final ModelSchemaStore schemaStore;
    private final TypeBuilderFactory<T> typeBuilderFactory;

    public TypeModelRuleExtractor(String modelName, Class<T> baseInterface, Class<U> baseImplementation, Class<?> builderInterface, ModelSchemaStore schemaStore, TypeBuilderFactory<T> typeBuilderFactory) {
        this.modelName = modelName;
        this.schemaStore = schemaStore;
        this.typeBuilderFactory = typeBuilderFactory;
        this.baseInterface = ModelType.of(baseInterface);
        this.baseImplementation = ModelType.of(baseImplementation);
        this.builderInterface = ModelType.of(builderInterface);
    }

    @Override
    public <R, S> ExtractedModelRule registration(MethodRuleDefinition<R, S> ruleDefinition, ValidationProblemCollector problems) {
        try {
            ModelType<? extends T> type = readType(ruleDefinition);
            ModelSchema<? extends T> schema = schemaStore.getSchema(type);
            TypeBuilderInternal<T> builder = typeBuilderFactory.create(schema);
            ruleDefinition.getRuleInvoker().invoke(builder);
            ModelType<? extends U> implModelType = determineImplementationType(type, builder);
            return createRegistration(ruleDefinition, type, implModelType, builder);
        } catch (InvalidModelException e) {
            throw invalidModelRule(ruleDefinition, e);
        }
    }

    /**
     * Create model type registration.
     * @param <P> Public parameterized type extending {@literal T}
     * @param <I> Implementation parameterized type extending {@literal V}
     */
    @Nullable
    protected abstract <P extends T, I extends U> ExtractedModelRule createRegistration(MethodRuleDefinition<?, ?> ruleDefinition,
                                                                                        ModelType<P> type, ModelType<I> implModelType,
                                                                                        TypeBuilderInternal<T> builder);

    protected ModelType<? extends T> readType(MethodRuleDefinition<?, ?> ruleDefinition) {
        assertIsVoidMethod(ruleDefinition);
        if (ruleDefinition.getReferences().size() != 1) {
            throw new InvalidModelException(String.format("Method %s must have a single parameter of type '%s'.", getDescription(), builderInterface.toString()));
        }
        ModelReference<?> subjectReference = ruleDefinition.getSubjectReference();
        @SuppressWarnings("ConstantConditions") ModelType<?> builder = subjectReference.getType();
        if (!builderInterface.isAssignableFrom(builder)) {
            throw new InvalidModelException(String.format("Method %s must have a single parameter of type '%s'.", getDescription(), builderInterface.toString()));
        }
        if (builder.getTypeVariables().size() != 1) {
            throw new InvalidModelException(String.format("Parameter of type '%s' must declare a type parameter.", builderInterface.toString()));
        }
        ModelType<?> subType = builder.getTypeVariables().get(0);

        if (subType.isWildcard()) {
            throw new InvalidModelException(String.format("%s type '%s' cannot be a wildcard type (i.e. cannot use ? super, ? extends etc.).", StringUtils.capitalize(modelName), subType.toString()));
        }

        if (!baseInterface.isAssignableFrom(subType)) {
            throw new InvalidModelException(String.format("%s type '%s' is not a subtype of '%s'.", StringUtils.capitalize(modelName), subType.toString(), baseInterface.toString()));
        }

        return subType.asSubtype(baseInterface);
    }

    protected InvalidModelRuleDeclarationException invalidModelRule(MethodRuleDefinition<?, ?> ruleDefinition, InvalidModelException e) {
        StringBuilder sb = new StringBuilder();
        ruleDefinition.getDescriptor().describeTo(sb);
        sb.append(String.format(" is not a valid %s model rule method.", modelName));
        return new InvalidModelRuleDeclarationException(sb.toString(), e);
    }

    protected ModelType<? extends U> determineImplementationType(ModelType<? extends T> type, TypeBuilderInternal<T> builder) {
        for (Class<?> internalView : builder.getInternalViews()) {
            if (!internalView.isInterface()) {
                throw new InvalidModelException(String.format("Internal view '%s' must be an interface.", internalView.getName()));
            }
        }

        Class<? extends T> implementation = builder.getDefaultImplementation();
        if (implementation == null) {
            return null;
        }

        ModelType<? extends T> implementationType = ModelType.of(implementation);

        if (!baseImplementation.isAssignableFrom(implementationType)) {
            throw new InvalidModelException(String.format("%s implementation '%s' must extend '%s'.", StringUtils.capitalize(modelName), implementationType, baseImplementation));
        }

        ModelType<? extends U> asSubclass = implementationType.asSubtype(baseImplementation);
        if (!type.isAssignableFrom(asSubclass)) {
            throw new InvalidModelException(String.format("%s implementation '%s' must implement '%s'.", StringUtils.capitalize(modelName), asSubclass, type));
        }

        for (Class<?> internalView : builder.getInternalViews()) {
            if (!internalView.isAssignableFrom(implementation)) {
                throw new InvalidModelException(String.format("%s implementation '%s' must implement internal view '%s'.", StringUtils.capitalize(modelName), asSubclass, internalView.getName()));
            }
        }

        try {
            asSubclass.getRawClass().getConstructor();
        } catch (NoSuchMethodException nsmException) {
            throw new InvalidModelException(String.format("%s implementation '%s' must have public default constructor.", StringUtils.capitalize(modelName), asSubclass));
        }

        return asSubclass;
    }
}
