/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.model.internal.inspect;

import org.gradle.internal.BiAction;
import org.gradle.internal.reflect.DirectInstantiator;
import org.gradle.model.RuleSource;
import org.gradle.model.Rules;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.registry.ModelRegistry;
import org.gradle.model.internal.type.ModelType;

import java.util.Collections;
import java.util.List;

public class RuleDefinitionRuleExtractor extends AbstractAnnotationDrivenModelRuleExtractor<Rules> {
    private static final ModelType<RuleSource> RULE_SOURCE_MODEL_TYPE = ModelType.of(RuleSource.class);

    @Override
    public <R, S> ExtractedModelRule registration(final MethodRuleDefinition<R, S> ruleDefinition, ValidationProblemCollector problems) {
        validateIsVoidMethod(ruleDefinition, problems);
        if (ruleDefinition.getReferences().size() < 2) {
            problems.add(ruleDefinition, "A method " + getDescription() + " must have at least two parameters");
            return null;
        }

        ModelType<?> ruleType = ruleDefinition.getReferences().get(0).getType();
        if (!RULE_SOURCE_MODEL_TYPE.isAssignableFrom(ruleType)) {
            problems.add(ruleDefinition, "The first parameter of a method " + getDescription() + " must be a subtype of RuleSource");
        }
        if (problems.hasProblems()) {
            return null;
        }

        final ModelType<? extends RuleSource> ruleSourceType = ruleType.asSubtype(RULE_SOURCE_MODEL_TYPE);

        return new ExtractedModelRule() {
            @Override
            public void apply(ModelRegistry modelRegistry, ModelPath scope) {
                final ModelReference<?> targetReference = ruleDefinition.getReferences().get(1);
                List<ModelReference<?>> inputs = ruleDefinition.getReferences().subList(2, ruleDefinition.getReferences().size());

                modelRegistry.configure(ModelActionRole.Defaults,
                        DirectNodeInputUsingModelAction.of(targetReference, ruleDefinition.getDescriptor(), inputs, new BiAction<MutableModelNode, List<ModelView<?>>>() {
                            @Override
                            public void execute(MutableModelNode subjectNode, List<ModelView<?>> modelViews) {
                                Object[] parameters = new Object[2 + modelViews.size()];
                                parameters[0] = DirectInstantiator.INSTANCE.newInstance(ruleSourceType.getConcreteClass());
                                parameters[1] = subjectNode.asImmutable(targetReference.getType(), ruleDefinition.getDescriptor()).getInstance();
                                for (int i = 2; i < parameters.length; i++) {
                                    parameters[i] = modelViews.get(i).getInstance();
                                }
                                ruleDefinition.getRuleInvoker().invoke(parameters);
                                subjectNode.applyToSelf(ruleSourceType.getConcreteClass());
                            }
                        }));
            }

            @Override
            public List<? extends Class<?>> getRuleDependencies() {
                return Collections.emptyList();
            }
        };
    }
}
