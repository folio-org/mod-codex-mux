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
