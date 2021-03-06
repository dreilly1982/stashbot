// Copyright 2014 Palantir Technologies
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.palantir.stash.stashbot.event;

import javax.annotation.Nonnull;

import com.atlassian.stash.event.pull.PullRequestEvent;
import com.atlassian.stash.pull.PullRequest;
import com.atlassian.stash.pull.PullRequestAction;

/*
 * Defines an event to describe when the stashbot metadata is updated
 */
public class StashbotMetadataUpdatedEvent extends PullRequestEvent {

    private static final long serialVersionUID = 1L;

    // Documentation says that the constructor is turning protected, this is okay
    public StashbotMetadataUpdatedEvent(@Nonnull Object source,
        @Nonnull PullRequest pullRequest) {
        super(source, pullRequest, PullRequestAction.UPDATED);
    }

}
