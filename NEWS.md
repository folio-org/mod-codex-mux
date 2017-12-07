## 1.0.0 2017-12-07

 * Initial release
 * Uses Okapi to discover modules of type "codex": the "target" modules
 * Supports sorting by title, date and id. If sorting is not specified,
   a round-robin scheme is used from each target. A merge sort strategy
   is used so there is never a re-order from what a target returns.
