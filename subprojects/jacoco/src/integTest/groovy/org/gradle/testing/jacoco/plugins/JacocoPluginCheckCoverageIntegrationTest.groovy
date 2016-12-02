/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.testing.jacoco.plugins

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.testing.jacoco.plugins.fixtures.JavaProjectUnderTest
import spock.lang.Unroll

class JacocoPluginCheckCoverageIntegrationTest extends AbstractIntegrationSpec {

    private final JavaProjectUnderTest javaProjectUnderTest = new JavaProjectUnderTest(testDirectory)

    def setup() {
        javaProjectUnderTest.writeBuildScript().writeSourceFiles()
    }

    def "no rules provided"() {
        buildFile << """
            jacocoTestReport {
                validationRules {}
            }
        """

        when:
        succeeds 'test', 'jacocoTestReport'

        then:
        executedAndNotSkipped(':test', ':jacocoTestReport')
    }

    def "single rule without thresholds"() {
        buildFile << """
            jacocoTestReport {
                validationRules {
                    rule {}
                }
            }
        """

        when:
        succeeds 'test', 'jacocoTestReport'

        then:
        executedAndNotSkipped(':test', ':jacocoTestReport')
    }

    def "can define includes for single rule"() {
        buildFile << """
            jacocoTestReport {
                validationRules {
                    rule {
                        scope = org.gradle.testing.jacoco.tasks.rules.JacocoRuleScope.CLASS
                        includes = ['org.gradle.*']
                        $Thresholds.Insufficient.LINE_METRIC_COVERED_RATIO
                    }
                }
            }
        """

        when:
        fails 'test', 'jacocoTestReport'

        then:
        executedAndNotSkipped(':test', ':jacocoTestReport')
        errorOutput.contains("Rule violated for class org.gradle.Class1: lines covered ratio is 1.0, but expected maximum is 0.5")
    }

    def "can define excludes for single rule"() {
        buildFile << """
            jacocoTestReport {
                validationRules {
                    rule {
                        excludes = ['*']
                        $Thresholds.Insufficient.LINE_METRIC_COVERED_RATIO
                    }
                }
            }
        """

        when:
        succeeds 'test', 'jacocoTestReport'

        then:
        executedAndNotSkipped(':test', ':jacocoTestReport')
    }

    @Unroll
    def "rule with sufficient coverage for #description"() {
        given:
        buildFile << """
            jacocoTestReport {
                validationRules {
                    rule {
                        ${thresholds.join('\n')}
                    }
                }
            }
        """

        when:
        succeeds 'test', 'jacocoTestReport'

        then:
        executedAndNotSkipped(':test', ':jacocoTestReport')

        where:
        thresholds                                        | description
        [Thresholds.Sufficient.LINE_METRIC_COVERED_RATIO] | 'line metric with covered ratio'
        [Thresholds.Sufficient.CLASS_METRIC_MISSED_COUNT] | 'class metric with missed count'
        [Thresholds.Sufficient.LINE_METRIC_COVERED_RATIO,
         Thresholds.Sufficient.CLASS_METRIC_MISSED_COUNT] | 'line and class metric'

    }

    @Unroll
    def "rule with insufficient coverage for #description"() {
        buildFile << """
            jacocoTestReport {
                validationRules {
                    rule {
                        ${thresholds.join('\n')}
                    }
                }
            }
        """

        when:
        fails 'test', 'jacocoTestReport'

        then:
        executedAndNotSkipped(':test', ':jacocoTestReport')
        errorOutput.contains("Rule violated for bundle $testDirectory.name: $errorMessage")

        where:
        thresholds                                          | description                                       | errorMessage
        [Thresholds.Insufficient.LINE_METRIC_COVERED_RATIO] | 'line metric with covered ratio'                  | 'lines covered ratio is 1.0, but expected maximum is 0.5'
        [Thresholds.Insufficient.CLASS_METRIC_MISSED_COUNT] | 'class metric with missed count'                  | 'classes missed count is 0.0, but expected minimum is 0.5'
        [Thresholds.Insufficient.LINE_METRIC_COVERED_RATIO,
         Thresholds.Insufficient.CLASS_METRIC_MISSED_COUNT] | 'first of multiple insufficient thresholds fails' | 'lines covered ratio is 1.0, but expected maximum is 0.5'
        [Thresholds.Sufficient.LINE_METRIC_COVERED_RATIO,
         Thresholds.Insufficient.CLASS_METRIC_MISSED_COUNT,
         Thresholds.Sufficient.CLASS_METRIC_MISSED_COUNT]   | 'first insufficient threshold fails'              | 'classes missed count is 0.0, but expected minimum is 0.5'
    }

    def "can ignore failures"() {
        buildFile << """
            jacocoTestReport {
                validationRules {
                    ignoreFailures = true

                    rule {
                        $Thresholds.Insufficient.LINE_METRIC_COVERED_RATIO
                    }
                }
            }
        """

        when:
        succeeds 'test', 'jacocoTestReport'

        then:
        executedAndNotSkipped(':test', ':jacocoTestReport')
        errorOutput.contains("Rule violated for bundle $testDirectory.name: lines covered ratio is 1.0, but expected maximum is 0.5")
    }

    static class Thresholds {
        static class Sufficient {
            static final String LINE_METRIC_COVERED_RATIO = Thresholds.threshold('org.gradle.testing.jacoco.tasks.rules.JacocoThresholdMetric.LINE', 'org.gradle.testing.jacoco.tasks.rules.JacocoThresholdValue.COVEREDRATIO', '0.0', '1.0')
            static final String CLASS_METRIC_MISSED_COUNT = Thresholds.threshold('org.gradle.testing.jacoco.tasks.rules.JacocoThresholdMetric.CLASS', 'org.gradle.testing.jacoco.tasks.rules.JacocoThresholdValue.MISSEDCOUNT', null, '0')
        }

        static class Insufficient {
            static final String LINE_METRIC_COVERED_RATIO = Thresholds.threshold('org.gradle.testing.jacoco.tasks.rules.JacocoThresholdMetric.LINE', 'org.gradle.testing.jacoco.tasks.rules.JacocoThresholdValue.COVEREDRATIO', '0.0', '0.5')
            static final String CLASS_METRIC_MISSED_COUNT = Thresholds.threshold('org.gradle.testing.jacoco.tasks.rules.JacocoThresholdMetric.CLASS', 'org.gradle.testing.jacoco.tasks.rules.JacocoThresholdValue.MISSEDCOUNT', '0.5', null)
        }

        static String threshold(String metric, String value, String minimum, String maximum) {
            StringBuilder threshold = new StringBuilder()
            threshold <<= 'threshold {\n'

            if (metric) {
                threshold <<= "    metric = $metric\n"
            }
            if (value) {
                threshold <<= "    value = $value\n"
            }
            if (minimum) {
                threshold <<= "    minimum = $minimum\n"
            }
            if (maximum) {
                threshold <<= "    maximum = $maximum\n"
            }

            threshold <<= '}'
            threshold.toString()
        }
    }
}
