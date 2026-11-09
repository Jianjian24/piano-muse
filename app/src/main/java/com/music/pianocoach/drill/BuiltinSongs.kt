package com.music.pianocoach.drill

object BuiltinSongs {

    private fun linear(id: String, title: String, desc: String, midis: List<Int>, ppq: Int = 480): SongPiece {
        var tick = 0
        val timed = midis.map { m ->
            val n = TimedNote(m, tick, ppq / 2)
            tick += ppq / 2
            n
        }
        return SongPiece(
            id = id,
            title = title,
            description = desc,
            notes = midis.map { SongNote(it, 0.5) },
            timedNotes = FingeringAssigner.assign(HandAssigner.assign(timed)),
            ticksPerQuarter = ppq,
        )
    }

    val all: List<SongPiece> = listOf(
        linear("scale_c_up", "C 大调音阶 · 上行", "单音热身 · 8 音", listOf(60, 62, 64, 65, 67, 69, 71, 72)),
        linear("scale_c_down", "C 大调音阶 · 下行", "单音热身 · 8 音", listOf(72, 71, 69, 67, 65, 64, 62, 60)),
        linear(
            "twinkle",
            "小星星（右手）",
            "单音简化",
            listOf(
                60, 60, 67, 67, 69, 69, 67,
                65, 65, 64, 64, 62, 62, 60,
                67, 67, 65, 65, 64, 64, 62,
                67, 67, 65, 65, 64, 64, 62,
                60, 60, 67, 67, 69, 69, 67,
                65, 65, 64, 64, 62, 62, 60,
            ),
        ),
        linear(
            "ode_to_joy",
            "欢乐颂（右手）",
            "单音简化",
            listOf(
                64, 64, 65, 67, 67, 65, 64, 62,
                60, 60, 62, 64, 64, 62, 62,
                64, 64, 65, 67, 67, 65, 64, 62,
                60, 60, 62, 64, 62, 60, 60,
            ),
        ),
        linear(
            "mary_lamb",
            "玛丽有只小羊羔",
            "单音简化",
            listOf(
                64, 62, 60, 62, 64, 64, 64,
                62, 62, 62, 64, 67, 67,
                64, 62, 60, 62, 64, 64, 64, 64,
                62, 62, 64, 62, 60,
            ),
        ),
    )

    fun byId(id: String): SongPiece? = all.find { it.id == id }
}
