/*
 * Copyright 2012 b1.org
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

package org.b1.pack.standard.builder;

import org.b1.pack.api.builder.BuilderCommand;
import org.b1.pack.api.builder.BuilderProvider;
import org.b1.pack.api.builder.BuilderVolume;
import org.b1.pack.api.builder.PackBuilder;

import java.util.List;

import static org.b1.pack.api.common.PackFormat.B1;

public class StandardPackBuilder extends PackBuilder {

    @Override
    public List<BuilderVolume> build(BuilderProvider provider, BuilderCommand command) {
        StandardBuilderPack pack = new StandardBuilderPack(provider);
        command.execute(pack);
        return pack.getVolumes();
    }

    @Override
    protected boolean isFormatSupported(String format) {
        return B1.equals(format);
    }
}
