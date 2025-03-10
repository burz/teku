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

package tech.pegasys.teku.networking.eth2.rpc.core;

import static org.mockito.Mockito.mock;
import static tech.pegasys.teku.spec.config.Constants.MAX_CHUNK_SIZE;

import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.networking.eth2.peers.PeerLookup;
import tech.pegasys.teku.networking.eth2.rpc.Utils;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.ProtobufEncoder;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.RpcEncoding;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.compression.Compressor;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.compression.snappy.SnappyFramedCompressor;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.context.RpcContextCodec;
import tech.pegasys.teku.networking.eth2.rpc.core.methods.Eth2RpcMethod;
import tech.pegasys.teku.networking.eth2.rpc.core.methods.SingleProtocolEth2RpcMethod;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BeaconBlocksByRootRequestMessage;

public class RpcDecoderTestBase {

  // Message long enough to require a three byte length prefix.
  protected static final BeaconBlocksByRootRequestMessage MESSAGE = createRequestMessage(600);
  protected static final RpcEncoding ENCODING = RpcEncoding.createSszSnappyEncoding(MAX_CHUNK_SIZE);
  protected static final Compressor COMPRESSOR = new SnappyFramedCompressor();
  protected static final Bytes MESSAGE_PLAIN_DATA = MESSAGE.sszSerialize();
  protected static final Bytes MESSAGE_DATA = COMPRESSOR.compress(MESSAGE_PLAIN_DATA);
  protected static final Bytes LENGTH_PREFIX = getLengthPrefix(MESSAGE_PLAIN_DATA.size());
  protected static final String ERROR_MESSAGE = "Bad request";
  protected static final Bytes ERROR_MESSAGE_PLAIN_DATA =
      Bytes.wrap(ERROR_MESSAGE.getBytes(StandardCharsets.UTF_8));
  protected static final Bytes ERROR_MESSAGE_DATA = COMPRESSOR.compress(ERROR_MESSAGE_PLAIN_DATA);
  protected static final Bytes ERROR_MESSAGE_LENGTH_PREFIX =
      getLengthPrefix(ERROR_MESSAGE_PLAIN_DATA.size());

  protected static final AsyncRunner ASYNC_RUNNER = new StubAsyncRunner();
  protected static final PeerLookup PEER_LOOKUP = mock(PeerLookup.class);

  protected static final RpcContextCodec<Bytes, BeaconBlocksByRootRequestMessage> CONTEXT_ENCODER =
      RpcContextCodec.noop(BeaconBlocksByRootRequestMessage.SSZ_SCHEMA);
  protected static final RpcResponseDecoder<BeaconBlocksByRootRequestMessage, Bytes>
      RESPONSE_DECODER = RpcResponseDecoder.create(ENCODING, CONTEXT_ENCODER);

  @SuppressWarnings("unchecked")
  protected static final Eth2RpcMethod<
          BeaconBlocksByRootRequestMessage, BeaconBlocksByRootRequestMessage>
      METHOD =
          new SingleProtocolEth2RpcMethod<
              BeaconBlocksByRootRequestMessage, BeaconBlocksByRootRequestMessage>(
              ASYNC_RUNNER,
              "",
              1,
              ENCODING,
              BeaconBlocksByRootRequestMessage.SSZ_SCHEMA,
              false,
              CONTEXT_ENCODER,
              mock(LocalMessageHandler.class),
              PEER_LOOKUP);

  protected List<List<ByteBuf>> testByteBufSlices(final Bytes... bytes) {
    List<List<ByteBuf>> ret = Utils.generateTestSlices(bytes);

    return ret;
  }

  protected static BeaconBlocksByRootRequestMessage createRequestMessage(
      final int blocksRequested) {
    final List<Bytes32> roots = new ArrayList<>();
    for (int i = 0; i < blocksRequested; i++) {
      roots.add(Bytes32.leftPad(Bytes.ofUnsignedInt(i)));
    }
    return new BeaconBlocksByRootRequestMessage(roots);
  }

  protected static Bytes getLengthPrefix(final int size) {
    return ProtobufEncoder.encodeVarInt(size);
  }
}
