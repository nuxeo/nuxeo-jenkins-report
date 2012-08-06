nuxeo-jenkins-report
====================

Nuxeo Jenkins report Market Place package allows you to save reports
about Jenkins status in Nuxeo.

You can create "Jenkins Reports Container" documents. Each container
is bound to a Jenkins view so that build status can be retrieved from
the list of jobs configured on this view. It can also hold an optional
"duty planning" in case you'd like to take turns monitoring the build
status.

Inside a container, you can create "Jenkins Report" documents,
offering status, claim, additional comments, etc... on failing
builds. The creation form is prefilled with most common
information. The build status can be retrieved using the Jenkins json
API: for each failing job on this view, more information is retrieved
from this job last build, and added as a metadata to the report. The
number of previously failing jobs has to be filled by hand for now,
and the current number of failing jobs is computed, displaying a trend
for this report.

The Market Place package is containing:

- a Studio project, called nuxeo-jenkins-report, defining document
  types and form-related features in Nuxeo Studio.
- a Nuxeo plugin, called nuxeo-jenkins-report-web, with a Seam
  component that will convert Jenkins json API response data into the
  Nuxeo document typology.

Build
=====

run:

    $ mvn clean install

Install
=======

Upload the generated Market Place package in the Nuxeo Update Center.

