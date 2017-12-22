package com.mrpowergamerbr.loritta.commands.vanilla.`fun`

import com.mrpowergamerbr.loritta.commands.AbstractCommand
import com.mrpowergamerbr.loritta.commands.CommandCategory
import com.mrpowergamerbr.loritta.commands.CommandContext
import com.mrpowergamerbr.loritta.utils.Constants
import com.mrpowergamerbr.loritta.utils.LoriReply
import com.mrpowergamerbr.loritta.utils.locale.BaseLocale

class CongaParrotCommand : AbstractCommand("congaparrot") {
	override fun getDescription(locale: BaseLocale): String {
		return locale["CONGAPARROT_Description"]
	}

	override fun getUsage(): String {
		return "número"
	}

	override fun getExample(): List<String> {
		return listOf("5", "10")
	}

	override fun getCategory(): CommandCategory {
		return CommandCategory.IMAGES
	}

	override fun run(context: CommandContext, locale: BaseLocale) {
		var times = context.args[0].toIntOrNull()

		if (context.args.getOrNull(0) != null) {
			if (times == null) {
				context.reply(
						LoriReply(
								message = locale["INVALID_NUMBER", context.args[0]],
								prefix = Constants.ERROR
						)
				)
				return
			}
		}

		if (times in 1..50) {
			var message = ""

			var upTo = times ?: 1

			for (idx in 1..upTo) {
				message += "<a:congaparrot:393804615067500544>"
			}

			context.reply(
					LoriReply(
							message = message,
							prefix = "<a:congaparrot:393804615067500544>"
					)
			)
		} else {
			context.reply(
					LoriReply(
							message = locale["CONGAPARROT_InvalidRange"],
							prefix = Constants.ERROR

					)
			)
		}
	}
}