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

package tech.pegasys.teku.networking.eth2.gossip;

import com.google.common.base.Throwables;
import io.libp2p.pubsub.MessageAlreadySeenException;
import io.libp2p.pubsub.NoPeersForOutboundMessageException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.networking.eth2.gossip.subnets.AttestationSubnetSubscriptions;
import tech.pegasys.teku.spec.datastructures.attestation.ValidateableAttestation;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;

public class AttestationGossipManager implements GossipManager {
  private static final Logger LOG = LogManager.getLogger();

  private final AttestationSubnetSubscriptions subnetSubscriptions;

  private final Counter attestationPublishSuccessCounter;
  private final Counter attestationPublishFailureCounter;

  public AttestationGossipManager(
      final MetricsSystem metricsSystem,
      final AttestationSubnetSubscriptions attestationSubnetSubscriptions) {
    subnetSubscriptions = attestationSubnetSubscriptions;
    final LabelledMetric<Counter> publishedAttestationCounter =
        metricsSystem.createLabelledCounter(
            TekuMetricCategory.BEACON,
            "published_attestation_total",
            "Total number of attestations sent to the gossip network",
            "result");
    attestationPublishSuccessCounter = publishedAttestationCounter.labels("success");
    attestationPublishFailureCounter = publishedAttestationCounter.labels("failure");
  }

  public void onNewAttestation(final ValidateableAttestation validateableAttestation) {
    if (validateableAttestation.isAggregate() || !validateableAttestation.markGossiped()) {
      return;
    }
    final Attestation attestation = validateableAttestation.getAttestation();
    subnetSubscriptions
        .gossip(attestation)
        .finish(
            __ -> {
              LOG.trace(
                  "Successfully published attestation for slot {}",
                  attestation.getData().getSlot());
              attestationPublishSuccessCounter.inc();
            },
            error -> {
              if (Throwables.getRootCause(error) instanceof MessageAlreadySeenException) {
                LOG.debug(
                    "Failed to publish attestation for slot {} because the message has already been seen",
                    attestation.getData().getSlot());
              } else if (Throwables.getRootCause(error)
                  instanceof NoPeersForOutboundMessageException) {
                LOG.warn(
                    "Failed to publish attestation for slot {} because no peers were available on the required gossip topic",
                    attestation.getData().getSlot());
              } else {
                LOG.error(
                    "Failed to publish attestation for slot {}",
                    attestation.getData().getSlot(),
                    error);
              }
              attestationPublishFailureCounter.inc();
            });
  }

  public void subscribeToSubnetId(final int subnetId) {
    LOG.trace("Subscribing to subnet ID {}", subnetId);
    subnetSubscriptions.subscribeToSubnetId(subnetId);
  }

  public void unsubscribeFromSubnetId(final int subnetId) {
    LOG.trace("Unsubscribing to subnet ID {}", subnetId);
    subnetSubscriptions.unsubscribeFromSubnetId(subnetId);
  }

  @Override
  public void subscribe() {
    subnetSubscriptions.subscribe();
  }

  @Override
  public void unsubscribe() {
    subnetSubscriptions.unsubscribe();
  }
}
