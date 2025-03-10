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

package tech.pegasys.teku.storage.protoarray;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Optional;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.Constants;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;

public class ProtoArrayBuilder {
  private int pruneThreshold = Constants.PROTOARRAY_FORKCHOICE_PRUNE_THRESHOLD;
  private Checkpoint justifiedCheckpoint;
  private Checkpoint finalizedCheckpoint;
  private UInt64 initialEpoch = SpecConfig.GENESIS_EPOCH;

  public ProtoArray build() {
    checkNotNull(justifiedCheckpoint, "Justified checkpoint must be supplied");
    checkNotNull(finalizedCheckpoint, "finalized checkpoint must be supplied");
    return new ProtoArray(pruneThreshold, justifiedCheckpoint, finalizedCheckpoint, initialEpoch);
  }

  public ProtoArrayBuilder pruneThreshold(final int pruneThreshold) {
    this.pruneThreshold = pruneThreshold;
    return this;
  }

  public ProtoArrayBuilder initialEpoch(final UInt64 initialEpoch) {
    this.initialEpoch = initialEpoch;
    return this;
  }

  public ProtoArrayBuilder justifiedCheckpoint(final Checkpoint justifiedCheckpoint) {
    checkNotNull(justifiedCheckpoint, "Justified checkpoint must be supplied");
    this.justifiedCheckpoint = justifiedCheckpoint;
    return this;
  }

  public ProtoArrayBuilder finalizedCheckpoint(final Checkpoint finalizedCheckpoint) {
    checkNotNull(finalizedCheckpoint, "finalized checkpoint must be supplied");
    this.finalizedCheckpoint = finalizedCheckpoint;
    return this;
  }

  public ProtoArrayBuilder initialCheckpoint(final Optional<Checkpoint> initialCheckpoint) {
    initialCheckpoint.ifPresentOrElse(
        checkpoint -> initialEpoch(checkpoint.getEpoch()),
        () -> initialEpoch(SpecConfig.GENESIS_EPOCH));
    return this;
  }
}
