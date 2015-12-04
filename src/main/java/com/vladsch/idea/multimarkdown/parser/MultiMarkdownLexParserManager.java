/*
 * Copyright (c) 2015-2015 Vladimir Schneider <vladimir.schneider@gmail.com>, all rights reserved.
 *
 * This code is private property of the copyright holder and cannot be used without
 * having obtained a license or prior written permission of the of the copyright holder.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */
package com.vladsch.idea.multimarkdown.parser;

import com.vladsch.idea.multimarkdown.settings.MultiMarkdownGlobalSettings;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pegdown.Extensions;
import org.pegdown.PegDownProcessor;
import org.pegdown.ast.RootNode;

public class MultiMarkdownLexParserManager {
    private static final Logger logger = org.apache.log4j.Logger.getLogger(MultiMarkdownLexParserManager.class);
    private static final boolean log = false;

    private static final ThreadLocal<ParsingInfo> lastParsingResult = new ThreadLocal<ParsingInfo>();
    public static final int GITHUB_WIKI_LINKS = 0x80000000;

    public static RootNode parseMarkdownRoot(@NotNull final CharSequence buffer, @Nullable Integer pegdownExtensions, @Nullable Integer parsingTimeout) {
        int actualPegdownExtensions = (pegdownExtensions != null ? pegdownExtensions : MultiMarkdownGlobalSettings.getInstance().getExtensionsValue()) | (MultiMarkdownGlobalSettings.getInstance().githubWikiLinks.getValue() ? GITHUB_WIKI_LINKS : 0);

        final ParsingInfo info = lastParsingResult.get();
        if (info != null && info.pegdownExtensions == actualPegdownExtensions && info.bufferHash == buffer.hashCode() && info.buffer.equals(buffer)) {
            if (log) logger.info("Root Parsing request satisfied by cache for thread " + Thread.currentThread());
            return info.rootNode;
        }

        if (log) logger.info("Root Parsing request not satisfied by cache for thread " + Thread.currentThread());
        PegDownProcessor processor = new PegDownProcessor(actualPegdownExtensions, parsingTimeout != null ? parsingTimeout : MultiMarkdownGlobalSettings.getInstance().parsingTimeout.getValue());

        char[] currentChars = buffer.toString().toCharArray();
        RootNode rootNode = processor.parseMarkdown(currentChars);
        lastParsingResult.set(new ParsingInfo(buffer, actualPegdownExtensions, rootNode, null, false));
        return rootNode;
    }

    public static
    @Nullable
    MultiMarkdownLexParser.LexerToken[] parseMarkdown(@NotNull final CharSequence buffer, @Nullable Integer pegdownExtensions, @Nullable Integer parsingTimeout) {
        int actualPegdownExtensions = (pegdownExtensions != null ? pegdownExtensions : MultiMarkdownGlobalSettings.getInstance().getExtensionsValue()) | (MultiMarkdownGlobalSettings.getInstance().githubWikiLinks.getValue() ? GITHUB_WIKI_LINKS : 0);
        RootNode rootNode = null;

        final ParsingInfo info = lastParsingResult.get();
        if (info != null && info.pegdownExtensions == actualPegdownExtensions && info.bufferHash == buffer.hashCode() && info.buffer.equals(buffer)) {
            if (info.hadLexerTokens) {
                if (log) logger.info("LexerToken Parsing request satisfied by cache for thread " + Thread.currentThread());
                return info.lexerTokens;
            }
            if (log) logger.info("LexerToken Parsing request partially satisfied by cache for thread " + Thread.currentThread());
            rootNode = info.rootNode;
        }

        char[] currentChars = buffer.toString().toCharArray();

        if (rootNode == null) {
            if (log) logger.info("LexerToken Parsing request not satisfied by cache for thread " + Thread.currentThread());
            PegDownProcessor processor = new PegDownProcessor(actualPegdownExtensions, parsingTimeout != null ? parsingTimeout : MultiMarkdownGlobalSettings.getInstance().parsingTimeout.getValue());
            rootNode = processor.parseMarkdown(currentChars);
        }

        MultiMarkdownLexParser lexParser = new MultiMarkdownLexParser();
        MultiMarkdownLexParser.LexerToken[] lexerTokens = lexParser.parseMarkdown(rootNode, currentChars, actualPegdownExtensions);

        lastParsingResult.set(new ParsingInfo(buffer, actualPegdownExtensions, rootNode, lexerTokens, true));
        return lexerTokens;
    }

    private static class ParsingInfo {
        @NotNull final CharSequence buffer;
        @Nullable final RootNode rootNode;
        @Nullable MultiMarkdownLexParser.LexerToken[] lexerTokens;
        final int bufferHash;
        final int pegdownExtensions;
        final boolean hadLexerTokens;

        public ParsingInfo(@NotNull CharSequence buffer, int pegdownExtensions, @Nullable RootNode rootNode, @Nullable MultiMarkdownLexParser.LexerToken[] lexerTokens, boolean hadLexerTokens) {
            this.buffer = buffer;
            this.bufferHash = buffer.hashCode();
            this.rootNode = rootNode;
            this.pegdownExtensions = pegdownExtensions;
            this.lexerTokens = lexerTokens;
            this.hadLexerTokens = hadLexerTokens || rootNode == null || lexerTokens != null;
        }
    }
}
