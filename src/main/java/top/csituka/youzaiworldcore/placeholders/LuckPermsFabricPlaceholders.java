/*
 * This file is part of LuckPerms, licensed under the MIT License.
 *
 *  Copyright (c) lucko (Luck) <luck@lucko.me>
 *  Copyright (c) contributors
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all
 *  copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  SOFTWARE.
 */

package top.csituka.youzaiworldcore.placeholders;

import eu.pb4.placeholders.api.PlaceholderResult;
import eu.pb4.placeholders.api.Placeholders;
import eu.pb4.placeholders.api.ServerPlaceholderContext;
import eu.pb4.placeholders.api.node.TextNode;
import eu.pb4.placeholders.api.parsers.LegacyFormattingParser;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.cacheddata.CachedDataManager;
import net.luckperms.api.model.user.User;
import net.luckperms.api.query.QueryOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;

public class LuckPermsFabricPlaceholders implements ModInitializer, PlaceholderPlatform {

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> registerPlaceholders());
    }

    private void registerPlaceholders() {
        // 检查 LuckPerms 是否已加载，如未加载则跳过注册
        if (!FabricLoader.getInstance().isModLoaded("luckperms")) {
            return;
        }

        LuckPerms luckPerms = LuckPermsProvider.get();
        PlaceholderProvider provider = new LPPlaceholderProvider(this, luckPerms);
        Map<String, Placeholder> placeholders = provider.getPlaceholders();

        placeholders.forEach((s, placeholder) -> {
            // Trim the unneeded _ off the end of dynamic placeholders
            String trimmed = s.replaceAll("_$", "");

            if (placeholder instanceof DynamicPlaceholder) {
                DynamicPlaceholder dp = (DynamicPlaceholder) placeholder;
                Placeholders.registerServer(Identifier.fromNamespaceAndPath("luckperms", trimmed), (ServerPlaceholderContext ctx, String arg) -> {
                    if (!ctx.hasServerPlayer()) {
                        return PlaceholderResult.invalid("No player!");
                    }

                    ServerPlayer player = ctx.serverPlayer();
                    User user = luckPerms.getUserManager().getUser(player.getUUID());
                    if (user == null) {
                        return PlaceholderResult.invalid("No user!");
                    }

                    CachedDataManager data = user.getCachedData();
                    QueryOptions queryOptions = luckPerms.getContextManager().getQueryOptions(player);

                    Object result;
                    if (arg == null && placeholders.containsKey(trimmed)) {
                        // Didn't use optional param - fallback to static version
                        result = ((StaticPlaceholder) placeholders.get(trimmed)).handle(player, user, data, queryOptions);
                    } else {
                        result = dp.handle(player, user, data, queryOptions, arg);
                    }

                    if (result instanceof Boolean) {
                        result = this.formatBoolean((boolean) result);
                    }

                    return result == null ? PlaceholderResult.invalid() : PlaceholderResult.value(parseText(result.toString()));
                });
            } else if (placeholder instanceof StaticPlaceholder) {
                StaticPlaceholder sp = (StaticPlaceholder) placeholder;
                Placeholders.registerServer(Identifier.fromNamespaceAndPath("luckperms", trimmed), (ServerPlaceholderContext ctx, String arg) -> {
                    if (!ctx.hasServerPlayer()) {
                        return PlaceholderResult.invalid("No player!");
                    }

                    ServerPlayer player = ctx.serverPlayer();
                    User user = luckPerms.getUserManager().getUser(player.getUUID());
                    if (user == null) {
                        return PlaceholderResult.invalid("No user!");
                    }

                    CachedDataManager data = user.getCachedData();
                    QueryOptions queryOptions = luckPerms.getContextManager().getQueryOptions(player);

                    Object result = sp.handle(player, user, data, queryOptions);

                    if (result instanceof Boolean) {
                        result = this.formatBoolean((boolean) result);
                    }

                    return result == null ? PlaceholderResult.invalid() : PlaceholderResult.value(parseText(result.toString()));
                });
            }
        });
    }

    private Component parseText(String input) {
        return TextNode.asSingle(LegacyFormattingParser.ALL.parseNodes(TextNode.of(input))).toComponent();
    }
}