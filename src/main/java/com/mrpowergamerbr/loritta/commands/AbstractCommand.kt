package com.mrpowergamerbr.loritta.commands

import com.mrpowergamerbr.loritta.Loritta
import com.mrpowergamerbr.loritta.LorittaLauncher
import com.mrpowergamerbr.loritta.userdata.ServerConfig
import com.mrpowergamerbr.loritta.utils.Constants
import com.mrpowergamerbr.loritta.utils.DateUtils
import com.mrpowergamerbr.loritta.utils.LorittaPermission
import com.mrpowergamerbr.loritta.utils.LorittaUser
import com.mrpowergamerbr.loritta.utils.LorittaUtils
import com.mrpowergamerbr.loritta.utils.LorittaUtilsKotlin
import com.mrpowergamerbr.loritta.utils.locale.BaseLocale
import com.mrpowergamerbr.loritta.utils.loritta
import com.mrpowergamerbr.loritta.utils.lorittaShards
import com.mrpowergamerbr.loritta.utils.remove
import com.mrpowergamerbr.loritta.utils.stripCodeMarks
import net.dv8tion.jda.core.EmbedBuilder
import net.dv8tion.jda.core.JDA
import net.dv8tion.jda.core.Permission
import net.dv8tion.jda.core.entities.*
import net.dv8tion.jda.core.events.message.MessageReceivedEvent
import net.dv8tion.jda.core.events.message.react.GenericMessageReactionEvent
import org.apache.commons.lang3.exception.ExceptionUtils
import org.slf4j.LoggerFactory
import java.awt.Color
import java.time.Instant
import java.util.*

open abstract class AbstractCommand(open val label: String, var aliases: List<String> = listOf(), var category: CommandCategory, var lorittaPermissions: List<LorittaPermission> = listOf()) {
	companion object {
		val logger = LoggerFactory.getLogger(AbstractCommand::class.java)
	}

	val cooldown = if (needsToUploadFiles()) 5000 else 2500
	var executedCount = 0

	open fun getDescription(locale: BaseLocale): String {
		return "Insira descrição do comando aqui!"
	}

	open fun getUsage(): String? {
		return null
	}

	open fun getDetailedUsage(): Map<String, String> {
		return mapOf()
	}

	open fun getExample(): List<String> {
		return listOf()
	}

	open fun getExtendedExamples(): Map<String, String> {
		return mapOf()
	}

	open fun hasCommandFeedback(): Boolean {
		return true
	}

	open abstract fun run(context: CommandContext, locale: BaseLocale)

	open fun getExtendedDescription(): String? {
		return null
	}

	open fun needsToUploadFiles(): Boolean {
		return false
	}

	open fun canUseInPrivateChannel(): Boolean {
		return true
	}

	/**
	 * Retorna as permissões necessárias para o usuário poder utilizar este comando
	 *
	 * @return A lista de permissões necessárias
	 */
	open fun getDiscordPermissions(): List<Permission> {
		return listOf()
	}

	/**
	 * Retorna as permissões necessárias para eu poder usar este comando
	 *
	 * @return A lista de permissões necessárias
	 */
	open fun getBotPermissions(): List<Permission> {
		return listOf()
	}

	/**
	 * Retorna se somente o dono do bot pode executar este comando

	 * @return Se somente o dono do bot pode usar este comando
	 */
	open fun onlyOwner(): Boolean {
		return false
	}

	/**
	 * Retorna se o comando precisa ter o sistema de música ativado

	 * @return Se o comando precisa ter o sistema de música ativado
	 */
	open fun requiresMusicEnabled(): Boolean {
		return false
	}

	/**
	 * Retorna se o comando só funciona em uma instância de música
	 */
	open fun onlyInMusicInstance(): Boolean {
		return false
	}

	fun handle(ev: LorittaMessageEvent, conf: ServerConfig, locale: BaseLocale, lorittaUser: LorittaUser): Boolean {
		val message = ev.message.contentDisplay
		val rawMessage = ev.message.contentRaw
		// É necessário remover o new line para comandos como "+eval", etc
		val rawArguments = rawMessage.replace("\n", "").split(" ")

		// Carregar as opções de comandos
		val cmdOptions = conf.getCommandOptionsFor(this)
		val prefix = if (cmdOptions.enableCustomPrefix) cmdOptions.customPrefix else conf.commandPrefix

		val labels = mutableListOf(label)
		labels.addAll(this.aliases)
		if (cmdOptions.enableCustomAliases) // Adicionar labels customizadas no painel
			labels.addAll(cmdOptions.aliases)

		// ignoreCase = true ~ Permite usar "+cOmAnDo"
		var valid = labels.any { rawArguments[0].equals(prefix + it, true) }
		var byMention = false

		if (rawArguments.getOrNull(1) != null && (rawArguments[0] == "<@${Loritta.config.clientId}>" || rawArguments[0] == "<@!${Loritta.config.clientId}>")) {
			// by mention
			valid = labels.any { rawArguments[1].equals(it, true) }
			byMention = true
		}

		if (valid) {
			try {
				val isPrivateChannel = ev.isFromType(ChannelType.PRIVATE)
				val start = System.currentTimeMillis()

				var locale = locale
				if (!isPrivateChannel) { // TODO: Migrar isto para que seja customizável
					when (ev.channel.id) {
						"414839559721975818" -> locale = loritta.getLocaleById("default") // português (default)
						"404713176995987466" -> locale = loritta.getLocaleById("en-us") // inglês
						"414847180285935622" -> locale = loritta.getLocaleById("es-es") // espanhol
						"414847291669872661" -> locale = loritta.getLocaleById("pt-pt") // português de portugal
						"414847379670564874" -> locale = loritta.getLocaleById("pt-funk") // português funk
					}
				}

				if (conf != loritta.dummyServerConfig && ev.textChannel != null && !ev.textChannel.canTalk()) { // Se a Loritta não pode falar no canal de texto, avise para o dono do servidor para dar a permissão para ela
					LorittaUtils.warnOwnerNoPermission(ev.guild, ev.textChannel, conf)
					return true
				}

				if (conf.blacklistedChannels.contains(ev.channel.id) && !lorittaUser.hasPermission(LorittaPermission.BYPASS_COMMAND_BLACKLIST)) {
					if (conf.warnIfBlacklisted) {
						if (conf.blacklistWarning.isNotEmpty() && ev.guild != null && ev.member != null && ev.textChannel != null) {
							var message = conf.blacklistWarning
							message = message.replace("{@user}", ev.member.asMention)
							message = message.replace("{user}", ev.member.user.name)
							message = message.replace("{nickname}", ev.member.effectiveName)
							message = message.replace("{guild}", ev.guild.name)
							message = message.replace("{guildsize}", ev.guild.members.size.toString())
							message = message.replace("{@owner}", ev.guild.owner.asMention)
							message = message.replace("{owner}", ev.guild.owner.effectiveName)
							message = message.replace("{@channel}", ev.textChannel.asMention)
							message = message.replace("{channel}", ev.textChannel.name)
							ev.textChannel.sendMessage(message).complete()
						}
					}
					return true // Ignorar canais bloqueados (return true = fast break, se está bloqueado o canal no primeiro comando que for executado, os outros obviamente também estarão)
				}

				if (cmdOptions.override && cmdOptions.blacklistedChannels.contains(ev.channel.id))
					return true // Ignorar canais bloqueados

				// Cooldown
				val diff = System.currentTimeMillis() - loritta.userCooldown.getOrDefault(ev.author.id, 0L) as Long

				if (1250 > diff && ev.author.id != Loritta.config.ownerId) { // Tá bom, é alguém tentando floodar, vamos simplesmente ignorar
					loritta.userCooldown.put(ev.author.id, System.currentTimeMillis()) // E vamos guardar o tempo atual
					return true
				}

				if (hasCommandFeedback() && !conf.commandOutputInPrivate) {
					ev.channel.sendTyping().complete()
				}

				if (cooldown > diff && ev.author.id != Loritta.config.ownerId) {
					val fancy = DateUtils.formatDateDiff((cooldown - diff) + System.currentTimeMillis(), locale)
					ev.channel.sendMessage("\uD83D\uDD25 **|** ${ev.author.asMention} ${locale["PLEASE_WAIT_COOLDOWN", fancy]}").complete()
					return true
				}

				loritta.userCooldown.put(ev.author.id, System.currentTimeMillis())

				LorittaUtilsKotlin.executedCommands++
				executedCount++

				// Se estamos dentro de uma guild... (Já que mensagens privadas não possuem permissões)
				if (!isPrivateChannel && ev.guild != null && ev.member != null && ev.textChannel != null) {
					// Verificar se a Loritta possui todas as permissões necessárias
					var botPermissions = ArrayList<Permission>(getBotPermissions())
					botPermissions.add(Permission.MESSAGE_EMBED_LINKS)
					botPermissions.add(Permission.MESSAGE_EXT_EMOJI)
					botPermissions.add(Permission.MESSAGE_ADD_REACTION)
					botPermissions.add(Permission.MESSAGE_HISTORY)
					val missingPermissions = ArrayList<Permission>(botPermissions.filterNot { ev.guild.selfMember.hasPermission(ev.textChannel, it) })

					if (missingPermissions.isNotEmpty()) {
						// oh no
						var required = missingPermissions.joinToString(", ", transform = { "`" + locale["PERMISSION_${it.name}"] + "`" })
						ev.textChannel.sendMessage(Constants.ERROR + " **|** ${ev.member.asMention} ${locale["PERMISSION_I_NEED_PERMISSION", required]}").complete()
						return true
					}
				}

				if (!isPrivateChannel && ev.member != null && ev.textChannel != null) {
					var missingPermissions = lorittaPermissions.filterNot { lorittaUser.hasPermission(it) }

					if (missingPermissions.isNotEmpty()) {
						// oh no
						var required = missingPermissions.joinToString(", ", transform = { "`" + locale["LORIPERMISSION_${it.name}"] + "`"})
						var message = locale["LORIPERMISSION_MissingPermissions", required]

						if (ev.member.hasPermission(Permission.ADMINISTRATOR) || ev.member.hasPermission(Permission.MANAGE_SERVER)) {
							message += " ${locale["LORIPERMISSION_MissingPermCanConfigure", Loritta.config.websiteUrl]}"
						}
						ev.textChannel.sendMessage(Constants.ERROR + " **|** ${ev.member.asMention} $message").complete()
						return true
					}
				}

				var args = message.replace("@${ev.guild?.selfMember?.effectiveName ?: ""}", "").stripCodeMarks().split(" ").toTypedArray().remove(0)
				var rawArgs = ev.message.contentRaw.stripCodeMarks().split(" ").toTypedArray().remove(0)
				var strippedArgs = ev.message.contentStripped.stripCodeMarks().split(" ").toTypedArray().remove(0)
				if (byMention) {
					args = args.remove(0)
					rawArgs = rawArgs.remove(0)
					strippedArgs = strippedArgs.remove(0)
				}
				val context = CommandContext(conf, lorittaUser, locale, ev, this, args, rawArgs, strippedArgs)
				if (args.isNotEmpty() && args[0] == "🤷") { // Usar a ajuda caso 🤷 seja usado
					explain(context)
					return true
				}
				if (LorittaUtilsKotlin.handleIfBanned(context, lorittaUser.profile)) {
					return true
				}
				if (!context.canUseCommand()) {
					context.sendMessage("\uD83D\uDE45 **|** " + context.getAsMention(true) + "**" + locale["NO_PERMISSION"] + "**")
					return true
				}
				if (context.isPrivateChannel && !canUseInPrivateChannel()) {
					context.sendMessage(Constants.ERROR + " **|** " + context.getAsMention(true) + locale["CANT_USE_IN_PRIVATE"])
					return true
				}
				if (needsToUploadFiles()) {
					if (!LorittaUtils.canUploadFiles(context)) {
						return true
					}
				}
				if (requiresMusicEnabled()) {
					if (!context.config.musicConfig.isEnabled) {
						val canManage = context.handle.hasPermission(Permission.MANAGE_SERVER) || context.handle.hasPermission(Permission.ADMINISTRATOR)
						context.sendMessage(Constants.ERROR + " **|** " + context.getAsMention(true) + locale["DJ_LORITTA_DISABLED"] + " \uD83D\uDE1E" + if (canManage) locale["DJ_LORITTA_HOW_TO_ENABLE", "https://loritta.website/dashboard"] else "")
						return true
					}
				}

				run(context, context.locale)

				val cmdOpti = context.config.getCommandOptionsFor(this)
				if (!isPrivateChannel && ev.guild != null) {
					if (ev.guild.selfMember.hasPermission(ev.textChannel, Permission.MESSAGE_MANAGE) && (conf.deleteMessageAfterCommand || (cmdOpti.override && cmdOpti.deleteMessageAfterCommand))) {
						ev.message.textChannel.getMessageById(ev.messageId).queue({
							// Nós iremos pegar a mensagem novamente, já que talvez ela tenha sido deletada
							it.delete().complete()
						})
					}
				}

				loritta.userCooldown.put(ev.author.id, System.currentTimeMillis())
				val end = System.currentTimeMillis()
				if (ev.message.isFromType(ChannelType.TEXT)) {
					logger.info("(${ev.message.guild.name} -> ${ev.message.channel.name}) ${ev.author.name}#${ev.author.discriminator} (${ev.author.id}): ${ev.message.contentDisplay} - OK! Finshed in ${end - start}ms")
				} else {
					logger.info("(Direct Message) ${ev.author.name}#${ev.author.discriminator} (${ev.author.id}): ${ev.message.contentDisplay} - OK! Finshed in ${end - start}ms")
				}
				return true
			} catch (e: Exception) {
				logger.error("Exception ao executar comando ${this.javaClass.simpleName}", e)
				LorittaUtilsKotlin.sendStackTrace(ev.message, e)

				// Avisar ao usuário que algo deu muito errado
				val mention = if (conf.mentionOnCommandOutput) "${ev.author.asMention} " else ""

				if (ev.isFromType(ChannelType.TEXT) && ev.textChannel != null && ev.textChannel.canTalk())
					ev.channel.sendMessage("\uD83E\uDD37 **|** " + mention + locale["ERROR_WHILE_EXECUTING_COMMAND"]).complete()
				return true
			}
		}
		return false
	}

	fun explain(context: CommandContext) {
		val conf = context.config
		val ev = context.event
		val locale = context.locale

		if (conf.explainOnCommandRun) {
			val rawArguments = context.message.contentRaw.split(" ")
			var commandLabel = rawArguments[0]
			if (rawArguments.getOrNull(1) != null && (rawArguments[0] == "<@${Loritta.config.clientId}>" || rawArguments[0] == "<@!${Loritta.config.clientId}>")) {
				// Caso o usuário tenha usado "@Loritta comando", pegue o segundo argumento (no caso o "comando") em vez do primeiro (que é a mention da Lori)
				commandLabel = rawArguments[1]
			}
			commandLabel = commandLabel.toLowerCase()

			val embed = EmbedBuilder()
			embed.setColor(Color(0, 193, 223))
			embed.setTitle("\uD83E\uDD14 " + locale["HOW_TO_USE"] + "... `" + commandLabel + "`")

			val usage = if (getUsage() != null) " `${getUsage()}`" else ""

			var cmdInfo = getDescription(context.locale) + "\n\n"

			cmdInfo += "\uD83D\uDC81 **" + locale["HOW_TO_USE"] + ":** " + commandLabel + usage + "\n"

			if (!this.getDetailedUsage().isEmpty()) {
				for ((key, value) in this.getDetailedUsage()) {
					cmdInfo += "${Constants.LEFT_PADDING} `$key` - $value\n"
				}
			}

			cmdInfo += "\n"

			// Criar uma lista de exemplos
			val examples = ArrayList<String>()
			for (example in this.getExample()) { // Adicionar todos os exemplos simples
				examples.add(commandLabel + if (example.isEmpty()) "" else " `$example`")
			}
			for ((key, value) in this.getExtendedExamples()) { // E agora vamos adicionar os exemplos mais complexos/extendidos
				examples.add(commandLabel + if (key.isEmpty()) "" else " `$key` - **$value**")
			}

			if (examples.isEmpty()) {
				cmdInfo += "\uD83D\uDCD6 **" + context.locale["EXAMPLE"] + ":**\n" + commandLabel
			} else {
				cmdInfo += "\uD83D\uDCD6 **" + context.locale["EXAMPLE"] + (if (this.getExample().size == 1) "" else "s") + ":**\n"
				for (example in examples) {
					cmdInfo += example + "\n"
				}
			}

			val aliases = mutableSetOf<String>()
			aliases.add(this.label)
			aliases.addAll(this.aliases)

			val onlyUnusedAliases = aliases.filter { it != commandLabel.replaceFirst(context.config.commandPrefix, "") }
			if (onlyUnusedAliases.isNotEmpty()) {
				cmdInfo += "\n\n\uD83D\uDD00 **${context.locale["CommandAliases"]}:**\n${onlyUnusedAliases.joinToString(", ", transform = { context.config.commandPrefix + it })}"
			}

			embed.setDescription(cmdInfo)
			embed.setAuthor("${context.userHandle.name}#${context.userHandle.discriminator}", null, ev.author.effectiveAvatarUrl)
			embed.setFooter(context.locale[this.category.fancyTitle], "https://loritta.website/assets/img/loritta_gabizinha_v1.png") // Adicionar quem executou o comando
			embed.setTimestamp(Instant.now())

			if (conf.explainInPrivate) {
				ev.author.openPrivateChannel().complete().sendMessage(embed.build()).complete()
			} else {
				context.sendMessage(context.getAsMention(true), embed.build())
			}
		}
	}

	@Deprecated(message = "message.onReactionAdd")
	open fun onCommandReactionFeedback(context: CommandContext, e: GenericMessageReactionEvent, msg: Message) {} // Quando alguém usa uma reaction na mensagem

	@Deprecated(message = "message.onResponse")
	open fun onCommandMessageReceivedFeedback(context: CommandContext, e: MessageReceivedEvent, msg: Message) {} // Quando uma mensagem é recebida

	class LorittaMessageEvent(
			val author: User,
			val member: Member?,
			val message: Message,
			val messageId: String,
			val guild: Guild?,
			val channel: MessageChannel,
			val textChannel: TextChannel?
	) {
		val jda: JDA get() = author.jda

		fun isFromType(type: ChannelType): Boolean {
			return this.channel.type == type
		}
	}
}