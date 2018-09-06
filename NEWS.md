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
