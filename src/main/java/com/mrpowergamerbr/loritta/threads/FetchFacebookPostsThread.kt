package com.mrpowergamerbr.loritta.threads

import com.mrpowergamerbr.loritta.utils.LorittaUtilsKotlin
import com.mrpowergamerbr.loritta.utils.loritta
import org.slf4j.LoggerFactory

class FetchFacebookPostsThread : Thread("Fetch Facebook Posts Thread") {
	companion object {
		val logger = LoggerFactory.getLogger(FetchFacebookPostsThread::class.java)
	}

	override fun run() {
		super.run()
		while (true) {
			fetchPosts();
			Thread.sleep(10000)
		}
	}

	fun fetchPosts() {
		logger.info("Pegando posts do Facebook...")
		try {
			val pagePostsSAM = LorittaUtilsKotlin.getRandomPostsFromPage("samemes2")
			val groupPostsSAM = LorittaUtilsKotlin.getRandomPostsFromGroup("293117011064847")

			loritta.southAmericaMemesPageCache.addAll(pagePostsSAM)
			loritta.southAmericaMemesGroupCache.addAll(groupPostsSAM)

			if (loritta.southAmericaMemesPageCache.size > 20) {
				loritta.southAmericaMemesPageCache = loritta.southAmericaMemesPageCache.subList(19, loritta.southAmericaMemesPageCache.size)
			}
			if (loritta.southAmericaMemesGroupCache.size > 20) {
				loritta.southAmericaMemesGroupCache = loritta.southAmericaMemesGroupCache.subList(19, loritta.southAmericaMemesGroupCache.size)
			}
		} catch (e: Exception) {
			logger.error("Erro ao pegar posts do Facebook!", e)
		}
	}
}