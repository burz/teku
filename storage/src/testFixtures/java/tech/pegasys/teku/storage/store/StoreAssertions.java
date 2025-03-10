/*
 * Copyright ConsenSys Software Inc., 2022
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package tech.pegasys.teku.storage.store;

import static org.assertj.core.api.Assertions.assertThat;

public class StoreAssertions {

  public static void assertStoresMatch(
      final UpdatableStore actualState, final UpdatableStore expectedState) {
    assertThat(actualState)
        .isEqualToIgnoringGivenFields(
            expectedState,
            "timeMillis",
            "stateCountGauge",
            "blockCountGauge",
            "checkpointCountGauge",
            "lock",
            "readLock",
            "blockProvider",
            "blocks",
            "blockMetadata",
            "stateRequestCachedCounter",
            "stateRequestRegenerateCounter",
            "stateRequestMissCounter",
            "checkpointStateRequestCachedCounter",
            "checkpointStateRequestRegenerateCounter",
            "checkpointStateRequestMissCounter",
            "metricsSystem",
            "states",
            "stateProvider",
            "checkpointStates",
            "forkChoiceStrategy");
    assertThat(actualState.getOrderedBlockRoots())
        .containsExactlyElementsOf(expectedState.getOrderedBlockRoots());
  }
}
