/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.integtests;

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

import static org.hamcrest.Matchers.containsString
import spock.lang.IgnoreIf
import org.gradle.integtests.fixtures.GradleDistributionExecuter

public class MultiProjectDependencyIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        settingsFile << 'include "a", "b", "c", "d"'
        buildFile << """
allprojects {
    apply plugin: 'java'

    task copyLibs(type: Copy) {
        from configurations.compile
        into 'deps'
    }

    compileJava.dependsOn copyLibs
}
"""
        executer.withArgument('--info')
    }

    def "project dependency c->[a,b]"() {
        projectDependency from: 'c', to: ['a', 'b']
        when:
        run ':c:build'

        then:
        jarsBuilt 'a', 'b', 'c'
        depsCopied 'a', []
        depsCopied 'b', []
        depsCopied 'c', ['a', 'b']
    }

    def "project dependency c->b->a"() {

        projectDependency from: 'c', to: ['b']
        projectDependency from: 'b', to: ['a']

        when:
        run ':c:build'

        then:
        jarsBuilt 'a', 'b', 'c'
        depsCopied 'c', ['a', 'b']
        depsCopied 'b', ['a']
        depsCopied 'a', []
    }

    def "project dependency a->[b,c] and b->c"() {

        projectDependency from: 'a', to: ['b', 'c']
        projectDependency from: 'b', to: ['c']

        when:
        run ':a:build'

        then:
        jarsBuilt 'a', 'b', 'c'
        depsCopied 'a', ['b', 'c']
        depsCopied 'b', ['c']
        depsCopied 'c', []
    }

    def "project dependency a->[b,d] and b->c->d"() {

        projectDependency from: 'a', to: ['b', 'd']
        projectDependency from: 'b', to: ['c']
        projectDependency from: 'c', to: ['d']

        when:
        run ':a:build'

        then:
        jarsBuilt 'a', 'b', 'c', 'd'
        depsCopied 'a', ['b', 'c', 'd']
        depsCopied 'b', ['c', 'd']
        depsCopied 'c', ['d']
        depsCopied 'd', []
    }

    def "project dependency a->[b,c] and b->d and c->d"() {

        projectDependency from: 'a', to: ['b', 'c']
        projectDependency from: 'b', to: ['d']
        projectDependency from: 'c', to: ['d']

        when:
        run ':a:build'

        then:
        jarsBuilt 'a', 'b', 'c', 'd'
        depsCopied 'a', ['b', 'c', 'd']
        depsCopied 'b', ['d']
        depsCopied 'c', ['d']
        depsCopied 'd', []
    }

    def "circular project dependency without task cycle a->b->c->a:2"() {

        projectDependency from: 'a', to: ['b']
        projectDependency from: 'b', to: ['c']

        def outputValue = System.currentTimeMillis()

        buildFile << """
project(':a') {
    task writeOutputFile << {
        file('build').mkdirs()
        file('build/output.txt') << "${outputValue}"
    }
}
project(':c') {
    compileJava.dependsOn ':a:writeOutputFile'
}
"""

        when:
        run ':a:build'

        then:
        jarsBuilt 'a', 'b', 'c'
        depsCopied 'a', ['b', 'c']
        depsCopied 'b', ['c']
        depsCopied 'c', []

        and:
        file('a/build/output.txt').text == "$outputValue"
    }

    def "project dependency cycle a->b->c->a"() {
        projectDependency from: 'a', to: ['b']
        projectDependency from: 'b', to: ['c']
        projectDependency from: 'c', to: ['a']

        when:
        fails ':a:build'

        then:
        failure.assertHasNoCause()
        failure.assertThatDescription(containsString("Circular dependency between tasks. Cycle includes [task ':a:compileJava', task ':a:jar']."))
    }

    def "project dependency a->b->c->d and c fails"() {
        projectDependency from: 'a', to: ['b']
        projectDependency from: 'b', to: ['c']
        projectDependency from: 'c', to: ['d']
        failingBuild 'c'

        when:
        fails ':a:build'

        then:
        failure.assertHasCause 'failure'

        and:
        jarsBuilt 'd'
        jarsNotBuilt 'a', 'b', 'c'
    }

    @IgnoreIf({GradleDistributionExecuter.systemPropertyExecuter.executeParallel})
    def "project dependency a->[b,c] and b->d and c fails"() {
        projectDependency from: 'a', to: ['b', 'c']
        projectDependency from: 'b', to: ['d']
        failingBuild 'c'

        when:
        fails ':a:build'

        then:
        failure.assertHasCause 'failure'

        and:
        jarsBuilt 'b', 'd' // These _may_ be built in parallel execution
        jarsNotBuilt 'a', 'c'
    }

    def "project dependency a->[b,c] and both b & c fail"() {
        projectDependency from: 'a', to: ['b', 'c']
        failingBuild 'b'
        failingBuild 'c'

        when:
        fails ':a:build'

        then:
        failure.assertHasCause 'failure'

        and:
        jarsNotBuilt 'a', 'b', 'c'

    }

    def projectDependency(def link) {
        def from = link['from']
        def to = link['to']

        def dependencies = to.collect {
            "compile project(':${it}')"
        }.join('\n')

        buildFile << """
project(':$from') {
    dependencies {
        ${dependencies}
    }
}
"""
    }

    def failingBuild(def project) {
        buildFile << """
project(':$project') {
    task fail << {
        throw new RuntimeException('failure')
    }
    jar.dependsOn fail
}
"""
    }

    def jarsBuilt(String... projects) {
        projects.each {
            file("${it}/build/libs/${it}.jar").assertExists()
        }
    }

    def jarsNotBuilt(String... projects) {
        projects.each {
            file("${it}/build/libs/${it}.jar").assertDoesNotExist()
        }
    }

    def depsCopied(String project, Collection<String> deps) {
        def depsDir = file("${project}/deps")
        if (deps.isEmpty()) {
            depsDir.assertDoesNotExist()
        } else {
            String[] depJars = deps.collect {
                "${it}.jar"
            }
            depsDir.assertHasDescendants(depJars)
        }
    }
}
