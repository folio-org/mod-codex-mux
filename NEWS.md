## 2.10.0 2021-03-01
 * MODCXMUX-73 Upgrade mod-codex-mux to RMB 32.x and Vert.x 4

## 2.9.2 2020-11-03
 * Update RMB to v31.1.5 and Vertx to 3.9.4

## 2.9.1 2020-10-23
 * Fix logging issue
 * Update RMB to v31.1.2 and Vertx to 3.9.3

## 2.9.0 2020-10-05
 * MODCXMUX-64 Migrate to JDK 11 and RMB 31.x

## 2.8.0 2020-06-09
 * MODCXMUX-61 Securing APIs by default
 * MODCXMUX-63 Update to RMB v30.0.2

## 2.7.0 2019-12-02
 * MODCXMUX-59 Update RMB to 29.0.1
 * MODCXMUX-54 Update jackson to 2.10.1 to fix jackson-databind security.
 * FOLIO-2358  Manage container memory
 * MODCXMUX-55 Add required permissions
 * FOLIO-2321  Remove old MD metadata
 * FOLIO-2256  Enable kube-deploy pipeline
 * FOLIO-2234  Add LaunchDescriptor settings

## 2.6.0 2019-06-11
 * MODCXMUX-36 - Implement GET Instance Sources
 * MODCXMUX-37 - Create RAML for GET codex-instances-sources

## 2.5.0 2019-03-19
 * MODCXMUX-47 - Fix failing API Tests - Updated path for downloaded schemas

## 2.4.0 2019-03-14
 * MODCXMUX-30 - Create common RAML definition for Packages endpoint
 * MODCXMUX-31 Update Merge And Sort Code to handle added Package Type
 * MODCXMUX-32 Implement GET Package by ID
 * MODCXMUX-33 Implement GET Package Collection
 * MODCXMUX-34 Implement GET Package Sources

## 2.3.0 2018-12-01
 * Update to RMB 23.2.1
 * MODCXMUX-29 Provide codex 3.2
 * MODCXMUX-28 Update for subject in instances

## 2.2.2 2018-09-06
 * MODCXMUX-24 Update to RAML 1.0
 * MODCXMUX-23 SQ fixes

## 2.2.1 2018-03-13

 * MODCXMUV-20 Update to RMB 19.0.0

## 2.2.0 2018-02-21

 * MODCXMUX-19 Move CQLUtil to okapi-common
 * MODCXMUX-14 Per-source record-counts, rewritten query
 * MODCXMUX-8 Report module version on start

## 2.1.2 2018-01-11

 * MODCXMUX-16 Use resultInfo.diagnostics for all errors

## 2.1.1 2018-01-11

 * MODCXMUX-13 Filter sources in mux for modules that can not do it

## 2.1.0 2018-01-11

 * This release was almost identical to 2.0.0 (MODCXMUX-13 fix not part of it)

## 2.0.0 2018-01-09

 * MODCXMUX-7 Return diagnostics in resultInfo. The module
   now provides version 3.0 of the codex interface.

## 1.2.0 2017-12-20

 * MODCXMUX-4 / MODCXMUX-5 Ignore unsupported features (400 errors)
 * MODCXMUX-6 Fix Null instance is returned

## 1.1.0 2017-12-15

 * MODCXMUX-3 Update to raml as of December 15, 2017. The new definition is
   more strict than previous version of codex, so interface version
   is now 2.0.

## 1.0.0 2017-12-07

 * Initial release
 * Uses Okapi to discover modules of type "codex": the "target" modules
 * Supports sorting by title, date and id. If sorting is not specified,
   a round-robin scheme is used from each target. A merge sort strategy
   is used so there is never a re-order from what a target returns.
