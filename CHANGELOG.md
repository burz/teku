# Changelog

## Upcoming Breaking Changes
- The `/eth/v1/debug/beacon/states/:state_id` endpoint has been deprecated in favor of the v2 Altair endpoint `/eth/v2/debug/beacon/states/:state_id`
- The `/eth/v1/beacon/blocks/:block_id` endpoint has been deprecated in favor of the v2 Altair endpoint `/eth/v2/beacon/blocks/:block_id`
- The `/eth/v1/validator/blocks/:slot` endpoint has been deprecated in favor of the v2 Altair endpoint `/eth/v2/validator/blocks/:slot`
- The `/eth/v1/debug/beacon/heads` endpoint has been deprecated in favor of the v2 Bellatrix endpoint `/eth/v2/debug/beacon/heads`

## Current Releases
For information on changes in released versions of Teku, see the [releases page](https://github.com/ConsenSys/teku/releases).

## Unreleased Changes

### Breaking Changes

### Additions and Improvements
 - Log a warning instead of a verbose error if node is syncing while performing sync committee duties
 - Distributions created from the same git commit and docker image will be identical
 - Optimised storage of latest vote information by batching updates
 - Ensured dependencies are up to date
 - Validator Registration signature integration with external signer

### Bug Fixes
 - Fix not rendering emoticons correctly in graffiti when running in a Docker container
 - Fix resource leak from closed SSE connections
 - Fix `latestValidHash`with invalid Execution Payload in response from execution engine didn't trigger appropriate ForkChoice changes 
