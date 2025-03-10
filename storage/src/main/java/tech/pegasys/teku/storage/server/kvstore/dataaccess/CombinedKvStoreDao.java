/*
 * Copyright ConsenSys Software Inc., 2020
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

package tech.pegasys.teku.storage.server.kvstore.dataaccess;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.errorprone.annotations.MustBeClosed;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.jetbrains.annotations.NotNull;
import tech.pegasys.teku.ethereum.pow.api.DepositsFromBlockEvent;
import tech.pegasys.teku.ethereum.pow.api.MinGenesisTimeBlockEvent;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.BlockAndCheckpoints;
import tech.pegasys.teku.spec.datastructures.blocks.BlockCheckpoints;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.forkchoice.VoteTracker;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.storage.server.kvstore.ColumnEntry;
import tech.pegasys.teku.storage.server.kvstore.KvStoreAccessor;
import tech.pegasys.teku.storage.server.kvstore.KvStoreAccessor.KvStoreTransaction;
import tech.pegasys.teku.storage.server.kvstore.dataaccess.V4FinalizedStateStorageLogic.FinalizedStateUpdater;
import tech.pegasys.teku.storage.server.kvstore.schema.KvStoreColumn;
import tech.pegasys.teku.storage.server.kvstore.schema.KvStoreVariable;
import tech.pegasys.teku.storage.server.kvstore.schema.SchemaCombined;

public class CombinedKvStoreDao<S extends SchemaCombined>
    implements KvStoreCombinedDaoBlinded, KvStoreCombinedDaoUnblinded, V4MigratableSourceDao {
  // Persistent data
  private final KvStoreAccessor db;
  private final S schema;
  private final V4FinalizedStateStorageLogic<S> stateStorageLogic;

  public CombinedKvStoreDao(
      final KvStoreAccessor db,
      final S schema,
      final V4FinalizedStateStorageLogic<S> stateStorageLogic) {
    this.db = db;
    this.schema = schema;
    this.stateStorageLogic = stateStorageLogic;
  }

  @Override
  public Optional<UInt64> getGenesisTime() {
    return db.get(schema.getVariableGenesisTime());
  }

  @Override
  public Optional<Checkpoint> getAnchor() {
    return db.get(schema.getVariableAnchorCheckpoint());
  }

  @Override
  public Optional<Checkpoint> getJustifiedCheckpoint() {
    return db.get(schema.getVariableJustifiedCheckpoint());
  }

  @Override
  public Optional<Checkpoint> getBestJustifiedCheckpoint() {
    return db.get(schema.getVariableBestJustifiedCheckpoint());
  }

  @Override
  public Optional<Checkpoint> getFinalizedCheckpoint() {
    return db.get(schema.getVariableFinalizedCheckpoint());
  }

  @Override
  public Optional<SignedBeaconBlock> getHotBlock(final Bytes32 root) {
    return db.get(schema.getColumnHotBlocksByRoot(), root);
  }

  @Override
  public Optional<BlockCheckpoints> getHotBlockCheckpointEpochs(final Bytes32 root) {
    return db.get(schema.getColumnHotBlockCheckpointEpochsByRoot(), root);
  }

  @Override
  public Optional<BeaconState> getHotState(final Bytes32 root) {
    return db.get(schema.getColumnHotStatesByRoot(), root);
  }

  @Override
  @MustBeClosed
  public Stream<SignedBeaconBlock> streamHotBlocks() {
    return db.stream(schema.getColumnHotBlocksByRoot()).map(ColumnEntry::getValue);
  }

  @Override
  public Optional<BeaconState> getLatestFinalizedState() {
    return db.get(schema.getVariableLatestFinalizedState());
  }

  @Override
  public Optional<Checkpoint> getWeakSubjectivityCheckpoint() {
    return db.get(schema.getVariableWeakSubjectivityCheckpoint());
  }

  @Override
  public List<Bytes32> getStateRootsBeforeSlot(final UInt64 slot) {
    try (Stream<ColumnEntry<Bytes32, SlotAndBlockRoot>> stream =
        db.stream(schema.getColumnStateRootToSlotAndBlockRoot())) {
      return stream
          .filter((column) -> column.getValue().getSlot().compareTo(slot) < 0)
          .map(ColumnEntry::getKey)
          .collect(Collectors.toList());
    }
  }

  @Override
  public Optional<SlotAndBlockRoot> getSlotAndBlockRootFromStateRoot(final Bytes32 stateRoot) {
    return db.get(schema.getColumnStateRootToSlotAndBlockRoot(), stateRoot);
  }

  @Override
  public Map<UInt64, VoteTracker> getVotes() {
    return db.getAll(schema.getColumnVotes());
  }

  @Override
  @MustBeClosed
  public Stream<DepositsFromBlockEvent> streamDepositsFromBlocks() {
    return db.stream(schema.getColumnDepositsFromBlockEvents()).map(ColumnEntry::getValue);
  }

  @Override
  @MustBeClosed
  public Stream<Map.Entry<Bytes32, BlockCheckpoints>> streamBlockCheckpoints() {
    return db.stream(schema.getColumnHotBlockCheckpointEpochsByRoot()).map(entry -> entry);
  }

  @Override
  public Optional<MinGenesisTimeBlockEvent> getMinGenesisTimeBlock() {
    return db.get(schema.getVariableMinGenesisTimeBlock());
  }

  @Override
  @MustBeClosed
  public HotUpdaterBlinded hotUpdaterBlinded() {
    return combinedUpdaterBlinded();
  }

  @Override
  @MustBeClosed
  public FinalizedUpdaterBlinded finalizedUpdaterBlinded() {
    return combinedUpdaterBlinded();
  }

  @Override
  @MustBeClosed
  public CombinedUpdaterBlinded combinedUpdaterBlinded() {
    return combinedUpdater();
  }

  @Override
  public HotUpdaterUnblinded hotUpdaterUnblinded() {
    return combinedUpdater();
  }

  @Override
  public FinalizedUpdaterUnblinded finalizedUpdaterUnblinded() {
    return combinedUpdater();
  }

  @Override
  public CombinedUpdaterUnblinded combinedUpdaterUnblinded() {
    return combinedUpdater();
  }

  @NotNull
  private V4CombinedUpdater<S> combinedUpdater() {
    return new V4CombinedUpdater<>(db, schema, stateStorageLogic.updater());
  }

  @Override
  public void ingest(
      final KvStoreCombinedDaoCommon sourceDao,
      final int batchSize,
      final Consumer<String> logger) {
    checkArgument(batchSize > 1, "Batch size must be greater than 1 element");
    checkArgument(
        sourceDao instanceof V4MigratableSourceDao, "Expected instance of V4FinalizedKvStoreDao");
    final V4MigratableSourceDao dao = (V4MigratableSourceDao) sourceDao;

    final Map<String, KvStoreVariable<?>> newVariables = getVariableMap();
    if (newVariables.size() > 0) {
      final Map<String, KvStoreVariable<?>> oldVariables = dao.getVariableMap();
      checkArgument(
          oldVariables.keySet().equals(newVariables.keySet()),
          "Cannot migrate database as source and target formats do not use the same variables");
      try (final KvStoreTransaction transaction = db.startTransaction()) {
        for (String key : newVariables.keySet()) {
          logger.accept(String.format("Copy variable %s", key));
          dao.getRawVariable(oldVariables.get(key))
              .ifPresent(value -> transaction.putRaw(newVariables.get(key), value));
        }
        transaction.commit();
      }
    }
    final Map<String, KvStoreColumn<?, ?>> newColumns = schema.getColumnMap();
    if (newColumns.size() > 0) {
      final Map<String, KvStoreColumn<?, ?>> oldColumns = dao.getColumnMap();
      checkArgument(
          oldColumns.keySet().equals(newColumns.keySet()),
          "Cannot migrate database as source and target formats do not use the same columns");
      for (String key : newColumns.keySet()) {
        final Optional<UInt64> maybeCount = displayCopyColumnMessage(key, oldColumns, dao, logger);
        try (final Stream<ColumnEntry<Bytes, Bytes>> oldEntryStream =
                dao.streamRawColumn(oldColumns.get(key));
            BatchWriter batchWriter = new BatchWriter(batchSize, logger, db, maybeCount)) {
          oldEntryStream.forEach(entry -> batchWriter.add(newColumns.get(key), entry));
        }
      }
    }
  }

  @Override
  public Map<String, KvStoreColumn<?, ?>> getColumnMap() {
    return schema.getColumnMap();
  }

  @Override
  public Map<String, KvStoreVariable<?>> getVariableMap() {
    return schema.getVariableMap();
  }

  @Override
  public <T> Optional<Bytes> getRawVariable(final KvStoreVariable<T> var) {
    return db.getRaw(var);
  }

  @Override
  @MustBeClosed
  public <K, V> Stream<ColumnEntry<Bytes, Bytes>> streamRawColumn(
      final KvStoreColumn<K, V> kvStoreColumn) {
    return db.streamRaw(kvStoreColumn);
  }

  @Override
  public void close() throws Exception {
    db.close();
  }

  @Override
  public Optional<SignedBeaconBlock> getFinalizedBlockAtSlot(final UInt64 slot) {
    return db.get(schema.getColumnFinalizedBlocksBySlot(), slot);
  }

  @Override
  public Optional<UInt64> getEarliestFinalizedBlockSlot() {
    return db.getFirstEntry(schema.getColumnFinalizedBlocksBySlot()).map(ColumnEntry::getKey);
  }

  @Override
  public Optional<SignedBeaconBlock> getEarliestFinalizedBlock() {
    return db.getFirstEntry(schema.getColumnFinalizedBlocksBySlot()).map(ColumnEntry::getValue);
  }

  @Override
  public Optional<SignedBeaconBlock> getLatestFinalizedBlockAtSlot(final UInt64 slot) {
    return db.getFloorEntry(schema.getColumnFinalizedBlocksBySlot(), slot)
        .map(ColumnEntry::getValue);
  }

  @Override
  public List<SignedBeaconBlock> getNonCanonicalUnblindedBlocksAtSlot(final UInt64 slot) {
    final Optional<Set<Bytes32>> maybeRoots =
        db.get(schema.getColumnNonCanonicalRootsBySlot(), slot);
    return maybeRoots.stream()
        .flatMap(Collection::stream)
        .flatMap(root -> db.get(schema.getColumnNonCanonicalBlocksByRoot(), root).stream())
        .collect(Collectors.toList());
  }

  @Override
  public Set<Bytes32> getNonCanonicalBlockRootsAtSlot(final UInt64 slot) {
    return db.get(schema.getColumnNonCanonicalRootsBySlot(), slot).orElseGet(HashSet::new);
  }

  @Override
  public List<SignedBeaconBlock> getBlindedNonCanonicalBlocksAtSlot(final UInt64 slot) {
    final Optional<Set<Bytes32>> maybeRoots =
        db.get(schema.getColumnNonCanonicalRootsBySlot(), slot);
    return maybeRoots.stream()
        .flatMap(Collection::stream)
        .flatMap(root -> db.get(schema.getColumnBlindedBlocksByRoot(), root).stream())
        .collect(Collectors.toList());
  }

  @Override
  public Optional<BeaconState> getLatestAvailableFinalizedState(final UInt64 maxSlot) {
    return stateStorageLogic.getLatestAvailableFinalizedState(db, schema, maxSlot);
  }

  @Override
  public long countNonCanonicalSlots() {
    return db.size(schema.getColumnNonCanonicalRootsBySlot());
  }

  @Override
  public long countBlindedBlocks() {
    return db.size(schema.getColumnBlindedBlocksByRoot());
  }

  @Override
  @MustBeClosed
  public Stream<SignedBeaconBlock> streamFinalizedBlocks(
      final UInt64 startSlot, final UInt64 endSlot) {
    return db.stream(schema.getColumnFinalizedBlocksBySlot(), startSlot, endSlot)
        .map(ColumnEntry::getValue);
  }

  @Override
  public Optional<UInt64> getEarliestBlindedBlockSlot() {
    return db.getFirstEntry(schema.getColumnFinalizedBlockRootBySlot()).map(ColumnEntry::getKey);
  }

  @Override
  public Optional<SignedBeaconBlock> getEarliestBlindedBlock() {
    final Optional<Bytes32> maybeRoot =
        db.getFirstEntry(schema.getColumnFinalizedBlockRootBySlot()).map(ColumnEntry::getValue);
    return maybeRoot.flatMap(root -> db.get(schema.getColumnBlindedBlocksByRoot(), root));
  }

  @Override
  public Optional<SignedBeaconBlock> getLatestBlindedBlockAtSlot(final UInt64 slot) {
    final Optional<Bytes32> maybeRoot =
        db.getFloorEntry(schema.getColumnFinalizedBlockRootBySlot(), slot)
            .map(ColumnEntry::getValue);
    return maybeRoot.flatMap(root -> db.get(schema.getColumnBlindedBlocksByRoot(), root));
  }

  @Override
  public Optional<Bytes32> getFinalizedBlockRootAtSlot(final UInt64 slot) {
    return db.get(schema.getColumnFinalizedBlockRootBySlot(), slot);
  }

  @Override
  @MustBeClosed
  public Stream<Bytes> streamExecutionPayloads() {
    return db.stream(schema.getColumnExecutionPayloadByPayloadHash()).map(ColumnEntry::getValue);
  }

  @Override
  public Optional<SignedBeaconBlock> getBlindedBlock(final Bytes32 root) {
    return db.get(schema.getColumnBlindedBlocksByRoot(), root);
  }

  @Override
  @MustBeClosed
  public Stream<SignedBeaconBlock> streamBlindedHotBlocks() {
    return db.stream(schema.getColumnHotBlockCheckpointEpochsByRoot())
        .map(ColumnEntry::getKey)
        .flatMap(root -> getBlindedBlock(root).stream());
  }

  @Override
  public Optional<Bytes> getExecutionPayload(final Bytes32 root) {
    return db.get(schema.getColumnExecutionPayloadByPayloadHash(), root);
  }

  @Override
  public Optional<UInt64> getSlotForFinalizedBlockRoot(final Bytes32 blockRoot) {
    return db.get(schema.getColumnSlotsByFinalizedRoot(), blockRoot);
  }

  @Override
  public Optional<UInt64> getSlotForFinalizedStateRoot(final Bytes32 stateRoot) {
    return db.get(schema.getColumnSlotsByFinalizedStateRoot(), stateRoot);
  }

  @Override
  public Optional<SlotAndBlockRoot> getSlotAndBlockRootForFinalizedStateRoot(
      final Bytes32 stateRoot) {
    Optional<UInt64> maybeSlot = db.get(schema.getColumnSlotsByFinalizedStateRoot(), stateRoot);
    return maybeSlot.flatMap(
        slot ->
            getFinalizedBlockAtSlot(slot)
                .map(block -> new SlotAndBlockRoot(slot, block.getRoot())));
  }

  @Override
  public Optional<UInt64> getOptimisticTransitionBlockSlot() {
    return db.get(schema.getOptimisticTransitionBlockSlot());
  }

  @Override
  public Optional<? extends SignedBeaconBlock> getNonCanonicalBlock(final Bytes32 root) {
    return db.get(schema.getColumnNonCanonicalBlocksByRoot(), root);
  }

  @Override
  @MustBeClosed
  public Stream<Bytes32> streamFinalizedBlockRoots(final UInt64 startSlot, final UInt64 endSlot) {
    return db.stream(schema.getColumnFinalizedBlockRootBySlot(), startSlot, endSlot)
        .map(ColumnEntry::getValue);
  }

  private Optional<UInt64> displayCopyColumnMessage(
      final String key,
      final Map<String, KvStoreColumn<?, ?>> oldColumns,
      final V4MigratableSourceDao dao,
      final Consumer<String> logger) {
    final Optional<UInt64> maybeCount = getObjectCountForColumn(key, oldColumns, dao);
    maybeCount.ifPresentOrElse(
        count -> logger.accept(String.format("Copy column %s - %s objects", key, count)),
        () -> logger.accept(String.format("Copy column %s", key)));

    return maybeCount;
  }

  private Optional<UInt64> getObjectCountForColumn(
      final String key,
      final Map<String, KvStoreColumn<?, ?>> oldColumns,
      final V4MigratableSourceDao dao) {
    switch (key) {
      case "FINALIZED_STATES_BY_SLOT":
      case "SLOTS_BY_FINALIZED_STATE_ROOT":
      case "SLOTS_BY_FINALIZED_ROOT":
        return getEntityCountFromColumn(oldColumns.get(key), dao);
      case "FINALIZED_BLOCKS_BY_SLOT":
        return getEntityCountFromColumn(oldColumns.get("SLOTS_BY_FINALIZED_ROOT"), dao);
      default:
        break;
    }
    return Optional.empty();
  }

  Optional<UInt64> getEntityCountFromColumn(
      final KvStoreColumn<?, ?> column, final V4MigratableSourceDao dao) {
    try (final Stream<ColumnEntry<Bytes, Bytes>> oldEntryStream = dao.streamRawColumn(column)) {
      return Optional.of(UInt64.valueOf(oldEntryStream.count()));
    }
  }

  @Override
  public Optional<SignedBeaconBlock> getFinalizedBlock(final Bytes32 root) {
    return db.get(schema.getColumnSlotsByFinalizedRoot(), root)
        .flatMap(this::getFinalizedBlockAtSlot);
  }

  static class V4CombinedUpdater<S extends SchemaCombined>
      implements CombinedUpdaterBlinded, CombinedUpdaterUnblinded, CombinedUpdaterCommon {
    private final KvStoreTransaction transaction;

    private final KvStoreAccessor db;
    private final S schema;
    private final FinalizedStateUpdater<S> stateStorageUpdater;

    V4CombinedUpdater(
        final KvStoreAccessor db, final S schema, FinalizedStateUpdater<S> stateStorageUpdater) {
      this.transaction = db.startTransaction();
      this.db = db;
      this.schema = schema;
      this.stateStorageUpdater = stateStorageUpdater;
    }

    @Override
    public void setGenesisTime(final UInt64 genesisTime) {
      transaction.put(schema.getVariableGenesisTime(), genesisTime);
    }

    @Override
    public void setAnchor(final Checkpoint anchor) {
      transaction.put(schema.getVariableAnchorCheckpoint(), anchor);
    }

    @Override
    public void setJustifiedCheckpoint(final Checkpoint checkpoint) {
      transaction.put(schema.getVariableJustifiedCheckpoint(), checkpoint);
    }

    @Override
    public void setBestJustifiedCheckpoint(final Checkpoint checkpoint) {
      transaction.put(schema.getVariableBestJustifiedCheckpoint(), checkpoint);
    }

    @Override
    public void setFinalizedCheckpoint(final Checkpoint checkpoint) {
      transaction.put(schema.getVariableFinalizedCheckpoint(), checkpoint);
    }

    @Override
    public void setWeakSubjectivityCheckpoint(Checkpoint checkpoint) {
      transaction.put(schema.getVariableWeakSubjectivityCheckpoint(), checkpoint);
    }

    @Override
    public void clearWeakSubjectivityCheckpoint() {
      transaction.delete(schema.getVariableWeakSubjectivityCheckpoint());
    }

    @Override
    public void setLatestFinalizedState(final BeaconState state) {
      transaction.put(schema.getVariableLatestFinalizedState(), state);
    }

    @Override
    public void addHotBlock(final BlockAndCheckpoints block) {
      final Bytes32 blockRoot = block.getRoot();
      transaction.put(schema.getColumnHotBlocksByRoot(), blockRoot, block.getBlock());
      addHotBlockCheckpointEpochs(blockRoot, block.getBlockCheckpoints());
    }

    @Override
    public void addHotBlockCheckpointEpochs(
        final Bytes32 blockRoot, final BlockCheckpoints blockCheckpoints) {
      transaction.put(
          schema.getColumnHotBlockCheckpointEpochsByRoot(), blockRoot, blockCheckpoints);
    }

    @Override
    public void addHotState(final Bytes32 blockRoot, final BeaconState state) {
      transaction.put(schema.getColumnHotStatesByRoot(), blockRoot, state);
    }

    @Override
    public void addHotStateRoots(
        final Map<Bytes32, SlotAndBlockRoot> stateRootToSlotAndBlockRootMap) {
      stateRootToSlotAndBlockRootMap.forEach(
          (stateRoot, slotAndBlockRoot) ->
              transaction.put(
                  schema.getColumnStateRootToSlotAndBlockRoot(), stateRoot, slotAndBlockRoot));
    }

    @Override
    public void pruneHotBlockContext(final Bytes32 blockRoot) {
      transaction.delete(schema.getColumnHotBlockCheckpointEpochsByRoot(), blockRoot);
      deleteHotState(blockRoot);
    }

    @Override
    public void pruneHotStateRoots(final List<Bytes32> stateRoots) {
      stateRoots.forEach(
          (root) -> transaction.delete(schema.getColumnStateRootToSlotAndBlockRoot(), root));
    }

    @Override
    public void addVotes(final Map<UInt64, VoteTracker> votes) {
      votes.forEach(
          (validatorIndex, vote) -> transaction.put(schema.getColumnVotes(), validatorIndex, vote));
    }

    @Override
    public void deleteHotBlock(final Bytes32 blockRoot) {
      transaction.delete(schema.getColumnHotBlocksByRoot(), blockRoot);
      transaction.delete(schema.getColumnHotBlockCheckpointEpochsByRoot(), blockRoot);
      deleteHotState(blockRoot);
    }

    @Override
    public void deleteHotState(final Bytes32 blockRoot) {
      transaction.delete(schema.getColumnHotStatesByRoot(), blockRoot);
    }

    @Override
    public void addMinGenesisTimeBlock(final MinGenesisTimeBlockEvent event) {
      transaction.put(schema.getVariableMinGenesisTimeBlock(), event);
    }

    @Override
    public void addDepositsFromBlockEvent(final DepositsFromBlockEvent event) {
      transaction.put(schema.getColumnDepositsFromBlockEvents(), event.getBlockNumber(), event);
    }

    @Override
    public void commit() {
      // Commit db updates
      transaction.commit();
      stateStorageUpdater.commit();
      close();
    }

    @Override
    public void cancel() {
      transaction.rollback();
      close();
    }

    @Override
    public void close() {
      transaction.close();
    }

    @Override
    public void addFinalizedBlock(final SignedBeaconBlock block) {
      transaction.put(schema.getColumnSlotsByFinalizedRoot(), block.getRoot(), block.getSlot());
      transaction.put(schema.getColumnFinalizedBlocksBySlot(), block.getSlot(), block);
    }

    @Override
    public void addFinalizedBlockRootBySlot(final SignedBeaconBlock block) {
      transaction.put(schema.getColumnFinalizedBlockRootBySlot(), block.getSlot(), block.getRoot());
    }

    @Override
    public void addBlindedBlock(final SignedBeaconBlock block, final Spec spec) {
      transaction.put(
          schema.getColumnBlindedBlocksByRoot(),
          block.getRoot(),
          block.blind(spec.atSlot(block.getSlot()).getSchemaDefinitions()));
      final Optional<ExecutionPayload> maybePayload =
          block.getMessage().getBody().getOptionalExecutionPayload();
      maybePayload.ifPresent(this::addExecutionPayload);
    }

    @Override
    public void addExecutionPayload(final ExecutionPayload payload) {
      transaction.put(
          schema.getColumnExecutionPayloadByPayloadHash(),
          payload.hashTreeRoot(),
          payload.sszSerialize());
    }

    @Override
    public void deleteBlindedBlock(final Bytes32 root) {
      final Optional<SignedBeaconBlock> maybeBlock =
          db.get(schema.getColumnBlindedBlocksByRoot(), root);
      maybeBlock.ifPresent(
          block -> {
            transaction.delete(schema.getColumnBlindedBlocksByRoot(), root);
            Optional<ExecutionPayloadHeader> maybeHeader =
                block.getMessage().getBody().getOptionalExecutionPayloadHeader();
            maybeHeader.ifPresent(header -> deleteExecutionPayload(header.hashTreeRoot()));
          });
    }

    @Override
    public void deleteExecutionPayload(final Bytes32 payloadHash) {
      transaction.delete(schema.getColumnExecutionPayloadByPayloadHash(), payloadHash);
    }

    @Override
    public void addNonCanonicalBlock(final SignedBeaconBlock block) {
      transaction.put(schema.getColumnNonCanonicalBlocksByRoot(), block.getRoot(), block);
    }

    @Override
    public void addNonCanonicalRootAtSlot(final UInt64 slot, final Set<Bytes32> blockRoots) {
      Optional<Set<Bytes32>> maybeRoots = db.get(schema.getColumnNonCanonicalRootsBySlot(), slot);
      final Set<Bytes32> roots = maybeRoots.orElse(new HashSet<>());
      if (roots.addAll(blockRoots)) {
        transaction.put(schema.getColumnNonCanonicalRootsBySlot(), slot, roots);
      }
    }

    @Override
    public void addFinalizedState(final Bytes32 blockRoot, final BeaconState state) {
      stateStorageUpdater.addFinalizedState(db, transaction, schema, state);
    }

    @Override
    public void addFinalizedStateRoot(final Bytes32 stateRoot, final UInt64 slot) {
      transaction.put(schema.getColumnSlotsByFinalizedStateRoot(), stateRoot, slot);
    }

    @Override
    public void setOptimisticTransitionBlockSlot(final Optional<UInt64> transitionBlockSlot) {
      if (transitionBlockSlot.isPresent()) {
        transaction.put(schema.getOptimisticTransitionBlockSlot(), transitionBlockSlot.get());
      } else {
        transaction.delete(schema.getOptimisticTransitionBlockSlot());
      }
    }
  }
}
