/*
 * Copyright 2006-2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.consol.citrus.admin.report;

import com.consol.citrus.admin.model.Project;
import com.consol.citrus.admin.model.TestReport;
import com.consol.citrus.util.FileUtils;
import com.consol.citrus.util.XMLUtils;
import com.consol.citrus.xml.xpath.XPathUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.w3c.dom.Document;

import java.io.IOException;
import java.text.SimpleDateFormat;

/**
 * @author Christoph Deppisch
 * @since 2.1
 */
public class JUnitTestReportService implements TestReportService {

    /** Logger */
    private static Logger log = LoggerFactory.getLogger(JUnitTestReportService.class);

    /** Date format */
    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ss");

    @Override
    public TestReport loadReport(Project activeProject) {
        TestReport report = new TestReport();

        if (hasTestResults(activeProject)) {
            try {
                Document testResults = XMLUtils.parseMessagePayload(getTestResultsAsString(activeProject));
                report.setSuiteName(XPathUtils.evaluateAsString(testResults, "/testsuite/@name", null));
                report.setDuration(Math.round(Double.valueOf(XPathUtils.evaluateAsString(testResults, "/testsuite/@time", null)) * 1000));

                report.setFailed(Long.valueOf(XPathUtils.evaluateAsString(testResults, "/testsuite/@failures", null)));
                report.setSkipped(Long.valueOf(XPathUtils.evaluateAsString(testResults, "/testsuite/@skipped", null)));
                report.setTotal(Long.valueOf(XPathUtils.evaluateAsString(testResults, "/testsuite/@tests", null)));
                report.setPassed(report.getTotal() - report.getSkipped() - report.getFailed());
            } catch (IOException e) {
                log.error("Failed to read test results file", e);
            }
        }

        return report;
    }

    @Override
    public boolean hasTestResults(Project activeProject) {
        return getTestResultsFile(activeProject).exists();
    }

    /**
     * Reads test results file content.
     * @return
     * @throws java.io.IOException
     */
    private String getTestResultsAsString(Project activeProject) throws IOException {
        return FileUtils.readToString(getTestResultsFile(activeProject));
    }

    /**
     * Access file resource representing the TestNG results file.
     * @param activeProject
     * @return
     */
    private Resource getTestResultsFile(Project activeProject) {
        return new FileSystemResource(activeProject.getProjectHome() + "/target/surefire-reports/TEST-TestSuite.xml");
    }
}
