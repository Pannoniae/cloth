package io.github.cottonmc.cotton.datapack.virtual;

import com.mojang.brigadier.CommandDispatcher;
import io.github.cottonmc.cotton.Cotton;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resource.ResourceType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Outputs the requested resource/data packs into a specific folder, so they can be read by pack makers.
 */
public final class PackPrinterCommand implements Consumer<CommandDispatcher<ServerCommandSource>> {
	private static final Logger LOGGER = LogManager.getLogger();

	@Override
	public void accept(CommandDispatcher<ServerCommandSource> dispatcher) {
		//create a command with one argument: which type of resource we want to export.
		dispatcher.register(
				CommandManager.literal("datapack").then(CommandManager.literal("exportvirtual")
						.then(CommandManager.literal("assets")
								.executes(c -> {
									export(c.getSource(), ResourceType.CLIENT_RESOURCES);
									return 1;
								})
						)
						.then(CommandManager.literal("data")
								.executes(c -> {
									export(c.getSource(), ResourceType.SERVER_DATA);
									return 1;
								}))
				)
		);
	}

	/**
	 * Exports all of the required virtual packs.
	 */
	private static void export(ServerCommandSource serverCommandSource, ResourceType type) {
		try {
			//if we're not on a dedicated server. This will be removed when moving to client sidecommands.
			if (!serverCommandSource.getMinecraftServer().isDedicated()) {
				System.out.println("we're not dedicated, we can export");
				Map<ResourceType, Collection<VirtualResourcePack>> typeCollectionMap = VirtualResourcePackManager.INSTANCE.getPacks().asMap();
				Collection<VirtualResourcePack> virtualResourcePacks = typeCollectionMap.getOrDefault(type, Collections.emptyList());

				Path gameDir = FabricLoader.getInstance().getGameDirectory().toPath();
				Path exportedVirtualPacks = gameDir.resolve("exportedVirtualPacks");

				//create the folder
				Files.createDirectories(exportedVirtualPacks);
				if (Files.notExists(exportedVirtualPacks)) {
					serverCommandSource.sendError(new TranslatableComponent("message." + Cotton.MODID + ".failed_to_create_folder", exportedVirtualPacks.toString()));
				} else {
					//loop through all of the virtual resource packs of the required type.
					for (VirtualResourcePack virtualResourcePack : virtualResourcePacks) {
						Map<String, Supplier<String>> contents = virtualResourcePack.getContents();

						//write out all of our entries.
						for (Map.Entry<String, Supplier<String>> entry : contents.entrySet()) {
							String location = entry.getKey();
							Supplier<String> stringSupplier = entry.getValue();
							Path outputPath = exportedVirtualPacks.resolve(location);
							Files.createDirectories(outputPath.getParent());

							try (BufferedWriter writer = Files.newBufferedWriter(outputPath)) {
								String s = stringSupplier.get();
								writer.write(s);
								writer.flush();
							} catch (IOException e) {
								LOGGER.error("Failed to export virtual resource " + location, e);
								serverCommandSource.sendError(new TranslatableComponent("message." + Cotton.MODID + ".exportvirtual.failed_to_export_resource", outputPath.toString()));
								return;
							}
						}
					}

					serverCommandSource.sendFeedback(new TranslatableComponent("message." + Cotton.MODID + ".exportvirtual.exported"), true);
				}
			} else {
				// System.out.println("we're dedicated.");
			}
		} catch (IOException e) {
			LOGGER.error("Failed to export virtual resources", e);
			serverCommandSource.sendError(new TranslatableComponent("message." + Cotton.MODID + ".exportvirtual.failed_to_export_resources"));
		}
	}
}
