/*
 * Copyright (c) 2010-2019. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.axonserver.connector.processor.grpc;

import io.axoniq.axonserver.grpc.control.EventProcessorInfo;
import io.axoniq.axonserver.grpc.control.EventProcessorInfo.SegmentStatus;
import io.axoniq.axonserver.grpc.control.PlatformInboundInstruction;
import org.axonframework.eventhandling.EventTrackerStatus;
import org.axonframework.eventhandling.TrackingEventProcessor;
import org.axonframework.eventhandling.TrackingToken;

import java.util.List;

import static java.util.stream.Collectors.toList;

/**
 * Supplier of {@link PlatformInboundInstruction} that represent the status of a {@link TrackingEventProcessor}.
 *
 * @author Sara Pellegrini
 * @since 4.0
 */
public class TrackingEventProcessorInfoMessage implements PlatformInboundMessage {

    private static final String EVENT_PROCESSOR_MODE = "Tracking";

    private final TrackingEventProcessor eventProcessor;

    /**
     * Instantiate a {@link PlatformInboundInstruction} representing the status of the given
     * {@link TrackingEventProcessor}
     *
     * @param eventProcessor a {@link TrackingEventProcessor} for which the status will be mapped to a
     *                       {@link PlatformInboundInstruction}
     */
    TrackingEventProcessorInfoMessage(TrackingEventProcessor eventProcessor) {
        this.eventProcessor = eventProcessor;
    }

    @Override
    public PlatformInboundInstruction instruction() {
        List<SegmentStatus> trackerInfo = eventProcessor.processingStatus()
                                                        .values()
                                                        .stream()
                                                        .map(this::buildTrackerInfo)
                                                        .collect(toList());

        EventProcessorInfo eventProcessorInfo =
                EventProcessorInfo.newBuilder()
                                  .setProcessorName(eventProcessor.getName())
                                  .setTokenStoreIdentifier(eventProcessor.getTokenStoreIdentifier())
                                  .setMode(EVENT_PROCESSOR_MODE)
                                  .setActiveThreads(eventProcessor.activeProcessorThreads())
                                  .setAvailableThreads(eventProcessor.availableProcessorThreads())
                                  .setRunning(eventProcessor.isRunning())
                                  .setError(eventProcessor.isError())
                                  .addAllSegmentStatus(trackerInfo)
                                  .build();

        return PlatformInboundInstruction.newBuilder()
                                         .setEventProcessorInfo(eventProcessorInfo)
                                         .build();
    }

    private SegmentStatus buildTrackerInfo(EventTrackerStatus status) {
        return SegmentStatus.newBuilder()
                            .setSegmentId(status.getSegment().getSegmentId())
                            .setCaughtUp(status.isCaughtUp())
                            .setReplaying(status.isReplaying())
                            .setOnePartOf(status.getSegment().getMask() + 1)
                            .setTokenPosition(getPosition(status.getTrackingToken()))
                            .setErrorState(status.isErrorState() ? buildErrorMessage(status.getError()) : "")
                            .build();
    }

    private long getPosition(TrackingToken trackingToken) {
        long position = 0;
        if (trackingToken != null) {
            position = trackingToken.position().orElse(0);
        }
        return position;
    }

    private String buildErrorMessage(Throwable error) {
        return error.getClass().getName() + ": " + error.getMessage();
    }
}
