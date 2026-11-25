package com.fredsystems.brilliantboard.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fredsystems.brilliantboard.data.remote.MoveFrequencyLocal
import com.fredsystems.brilliantboard.data.remote.MoveSuggestion
import com.fredsystems.brilliantboard.data.remote.ExplorerApi
import com.fredsystems.brilliantboard.data.repository.ChessRepository
import com.fredsystems.brilliantboard.data.repository.SettingsRepository
import com.fredsystems.brilliantboard.data.repository.SmartSearchRepository
import com.fredsystems.brilliantboard.data.repository.PlayerSuggestion
import com.fredsystems.brilliantboard.data.repository.SyncEngine
import com.fredsystems.brilliantboard.domain.chess.ChessMove
import com.fredsystems.brilliantboard.domain.chess.Position
import com.fredsystems.brilliantboard.domain.model.GameRecord
import com.fredsystems.brilliantboard.domain.model.GameReviewReport
import com.fredsystems.brilliantboard.domain.model.Platform
import com.fredsystems.brilliantboard.domain.model.PlayerProfile
import com.fredsystems.brilliantboard.domain.model.Side
import com.fredsystems.brilliantboard.domain.model.TimeClass
import com.fredsystems.brilliantboard.domain.review.GameReviewEngine
import com.fredsystems.brilliantboard.domain.review.WinProbability
import com.fredsystems.brilliantboard.domain.scouting.ClockStats
import com.fredsystems.brilliantboard.domain.scouting.EndgameStat
import com.fredsystems.brilliantboard.domain.scouting.GameFeatureExtractor
import com.fredsystems.brilliantboard.domain.scouting.GameplanCard
import com.fredsystems.brilliantboard.domain.scouting.OpeningStat
import com.fredsystems.brilliantboard.domain.scouting.OpponentGameplan
import com.fredsystems.brilliantboard.domain.scouting.PhaseStat
import com.fredsystems.brilliantboard.domain.scouting.StyleProfile
import com.fredsystems.brilliantboard.domain.scouting.TiltProfile
import com.fredsystems.brilliantboard.domain.scouting.Wdl
import com.fredsystems.brilliantboard.engine.PgnEvaluator
import com.fredsystems.brilliantboard.engine.StockfishEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchState(
    val suggestions: List<PlayerSuggestion> = emptyList(),
    val searching: Boolean = false,
)

class SearchViewModel(
    private val repository: SmartSearchRepository,
    private val sync: SyncEngine,
) : ViewModel() {
    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state.asStateFlow()
    private var job: Job? = null

    fun search(query: String) {
        job?.cancel()
        if (query.trim().length < 2) {
            _state.update { it.copy(suggestions = emptyList(), searching = false) }
            return
        }
        _state.update { it.copy(searching = true) }
        job = viewModelScope.launch {
            delay(400)
            val owner = runCatching { sync.ownAccounts().firstOrNull()?.username }.getOrNull()
            val results = runCatching { repository.suggest(query, owner) }.getOrDefault(emptyList())
            _state.update { it.copy(suggestions = results, searching = false) }
        }
    }

    fun clear() {
        job?.cancel()
        _state.update { SearchState() }
    }
}

data class HomeState(
    val accounts: List<PlayerProfile> = emptyList(),
    val totalGames: Int = 0,
    val busy: Boolean = false,
    val message: String? = null,
)

class HomeViewModel(
    private val sync: SyncEngine,
    private val repo: ChessRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            runCatching {
                val accounts = sync.ownAccounts()
                val count = accounts.sumOf { repo.count(it.username) }
                _state.update { it.copy(accounts = accounts, totalGames = count) }
            }.onFailure { _state.update { it.copy(message = it.message) } }
        }
    }

    fun link(username: String, platform: Platform) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, message = null) }
            val linked = runCatching { sync.linkOwnAccount(username, platform) }
            if (linked.isFailure) {
                _state.update {
                    it.copy(
                        busy = false,
                        message = "Could not find $username on ${platform.label}: ${linked.exceptionOrNull()?.message}",
                    )
                }
                return@launch
            }
            val synced = runCatching { sync.sync(username, platform) }
            _state.update {
                it.copy(
                    busy = false,
                    message = synced.fold(
                        onSuccess = { result -> "Imported ${result.gamesImported} games from ${platform.label}" },
                        onFailure = { error ->
                            "Account linked, but game sync was incomplete (${error.message ?: "network error"}). " +
                                "Use Sync all to retry."
                        },
                    ),
                )
            }
            refresh()
        }
    }

    fun syncAll() {
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            var total = 0
            sync.ownAccounts().forEach { account ->
                runCatching { total += sync.sync(account.username, account.platform).gamesImported }
            }
            _state.update { it.copy(busy = false, message = "Synced $total new games") }
            refresh()
        }
    }
}

data class RatingCurve(val label: String, val points: List<Float>)

data class ProfileDashboard(
    val profile: PlayerProfile? = null,
    val games: List<GameRecord> = emptyList(),
    val wdl: Wdl = Wdl(),
    val openings: List<OpeningStat> = emptyList(),
    val clock: ClockStats = ClockStats(0f, 0f, 0f, 0f, 0),
    val phases: List<PhaseStat> = emptyList(),
    val endgames: List<EndgameStat> = emptyList(),
    val tilt: TiltProfile = TiltProfile(0f, 0f, 0f, 0f, 0f, 0f, emptyList(), emptyList()),
    val tactics: com.fredsystems.brilliantboard.domain.scouting.TacticalProfile = com.fredsystems.brilliantboard.domain.scouting.TacticalProfile(),
    val style: StyleProfile? = null,
    val ratingCurves: List<RatingCurve> = emptyList(),
    val timeTroubleGames: Int = 0,
) {
    val blunderRate: Float get() = clock.timeTroubleBlunderRate
}

data class ProfileState(
    val accounts: List<PlayerProfile> = emptyList(),
    val selected: PlayerProfile? = null,
    val dashboard: ProfileDashboard = ProfileDashboard(),
    val busy: Boolean = false,
    val message: String? = null,
)

class MyProfileViewModel(
    private val sync: SyncEngine,
    private val repo: ChessRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ProfileState())
    val state: StateFlow<ProfileState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val accounts = sync.ownAccounts()
            _state.update {
                it.copy(accounts = accounts, selected = it.selected ?: accounts.firstOrNull())
            }
            _state.value.selected?.let { select(it) }
        }
    }

    fun select(profile: PlayerProfile) {
        viewModelScope.launch {
            _state.update { it.copy(selected = profile, busy = true, message = null) }
            runCatching {
                val allGames = repo.observeGames(profile.username).first()
                val games = allGames.take(ANALYSIS_GAME_LIMIT)
                val notice = if (allGames.size > games.size) {
                    "Analysing the $ANALYSIS_GAME_LIMIT most recent games of ${allGames.size}"
                } else null
                _state.update { it.copy(message = notice) }
                val dashboard = withContext(Dispatchers.Default) { buildDashboard(profile, games) }
                _state.update { it.copy(busy = false, dashboard = dashboard) }
            }.onFailure { error ->
                _state.update {
                    it.copy(busy = false, message = "Dashboard error: ${error.message ?: "unknown error"}")
                }
            }
        }
    }

    private fun buildDashboard(profile: PlayerProfile, games: List<GameRecord>): ProfileDashboard {
        val features = games.map { runCatching { GameFeatureExtractor.extract(it, it.userSide) }.getOrNull() }
            .filterNotNull()
        val openingStats = features
            .groupBy { it.eco ?: "?" }
            .mapNotNull { (eco, group) ->
                val wdl = group.fold(Wdl()) { acc, f -> acc + Wdl(
                    if (f.result == com.fredsystems.brilliantboard.domain.model.GameResult.WIN) 1 else 0,
                    if (f.result == com.fredsystems.brilliantboard.domain.model.GameResult.DRAW) 1 else 0,
                    if (f.result == com.fredsystems.brilliantboard.domain.model.GameResult.LOSS) 1 else 0,
                ) }
                OpeningStat(eco, group.firstNotNullOfOrNull { it.openingName }.orEmpty(),
                    group.first().side, wdl, group.size, null, null)
            }
            .sortedBy { it.wdl.scorePercent }

        val tilt = com.fredsystems.brilliantboard.domain.scouting.TiltAnalytics.analyze(features, Side.WHITE)
        val style = com.fredsystems.brilliantboard.domain.scouting.StyleProfiler.analyze(features, Side.WHITE, tilt)
        val clock = com.fredsystems.brilliantboard.domain.scouting.ClockIQ.analyze(features, Side.WHITE)
        val wdl = games.map { it.result }.fold(Wdl()) { acc, r -> acc + Wdl(
            if (r == com.fredsystems.brilliantboard.domain.model.GameResult.WIN) 1 else 0,
            if (r == com.fredsystems.brilliantboard.domain.model.GameResult.DRAW) 1 else 0,
            if (r == com.fredsystems.brilliantboard.domain.model.GameResult.LOSS) 1 else 0,
        ) }

        return ProfileDashboard(
            profile = profile,
            games = games,
            wdl = wdl,
            openings = openingStats,
            clock = clock,
            phases = com.fredsystems.brilliantboard.domain.scouting.PhaseMastery.analyze(games, Side.WHITE),
            endgames = com.fredsystems.brilliantboard.domain.scouting.EndgameConversionLab.analyze(features),
            tilt = tilt,
            tactics = com.fredsystems.brilliantboard.domain.scouting.TacticalMotifs.analyze(games, Side.WHITE),
            style = style,
            ratingCurves = buildRatingCurves(profile),
            timeTroubleGames = features.count { it.timeTroubleBlunders > 0 },
        )
    }

    companion object {
        private const val ANALYSIS_GAME_LIMIT = 400
    }

    private fun buildRatingCurves(profile: PlayerProfile): List<RatingCurve> {
        val fromHistory = profile.ratingHistory
            .groupBy { it.timeClass }
            .map { (timeClass, points) ->
                RatingCurve(
                    "${profile.platform.label} ${timeClass.label}",
                    points.sortedBy { it.date }.map { it.rating.toFloat() },
                )
            }
        val fromStats = profile.ratings.map { (timeClass, rating) ->
            RatingCurve("${timeClass.label} current", listOf(rating.toFloat(), rating.toFloat()))
        }
        return fromHistory + fromStats
    }
}

data class ScoutState(
    val query: String = "",
    val platform: Platform = Platform.CHESS_COM,
    val profile: PlayerProfile? = null,
    val dossier: GameplanCard? = null,
    val busy: Boolean = false,
    val message: String? = null,
)

class ScoutViewModel(
    private val sync: SyncEngine,
    private val repo: ChessRepository,
    private val explorer: ExplorerApi,
) : ViewModel() {
    private val _state = MutableStateFlow(ScoutState())
    val state: StateFlow<ScoutState> = _state.asStateFlow()

    fun setQuery(value: String) = _state.update { it.copy(query = value) }
    fun setPlatform(platform: Platform) = _state.update { it.copy(platform = platform) }

    fun search() {
        val query = _state.value.query.trim()
        if (query.isBlank()) return
        val platform = _state.value.platform
        viewModelScope.launch {
            _state.update { it.copy(busy = true, message = null, profile = null, dossier = null) }
            runCatching {
                val profile = sync.fetchProfile(query, platform)
                    ?: error("Player not found on ${platform.label}")
                val games = sync.scoutGames(query, platform)
                val own = sync.ownAccounts().map { it.username.lowercase() }.toSet()
                val dossier = OpponentGameplan.build(query, platform, games, own)
                _state.update { it.copy(busy = false, profile = profile, dossier = dossier) }
            }.onFailure { error ->
                _state.update { it.copy(busy = false, message = error.message) }
            }
        }
    }

    suspend fun explorerSuggestions(fen: String): List<MoveSuggestion> = explorer.suggestions(fen)

    fun blendRepertoire(
        suggestions: List<MoveSuggestion>,
        ownGames: List<GameRecord>,
        side: Side,
    ): List<MoveSuggestion> {
        val frequencies = ownGames.mapNotNull { game ->
            val move = game.moves.firstOrNull { it.side == side }
            move?.uci?.let { uci ->
                val wdl = when (game.result) {
                    com.fredsystems.brilliantboard.domain.model.GameResult.WIN -> 100f
                    com.fredsystems.brilliantboard.domain.model.GameResult.DRAW -> 50f
                    else -> 0f
                }
                uci to MoveFrequencyLocal(1, wdl)
            }
        }.groupBy({ it.first }, { it.second }).mapValues { (_, values) ->
            MoveFrequencyLocal(values.size, values.sumOf { it.winPercent.toDouble() }.toFloat() / values.size)
        }
        return explorer.mergeWithRepertoire(suggestions, frequencies)
    }
}

data class ReviewState(
    val games: List<GameRecord> = emptyList(),
    val selected: GameRecord? = null,
    val report: GameReviewReport? = null,
    val busy: Boolean = false,
    val progress: Float = 0f,
    val message: String? = null,
    val currentPly: Int = 0,
    val position: Position = Position.START,
    val lastMove: Pair<Int, Int>? = null,
    val arrows: List<com.fredsystems.brilliantboard.ui.board.BoardArrow> = emptyList(),
    val flipped: Boolean = false,
)

class ReviewViewModel(
    private val repo: ChessRepository,
    private val engine: StockfishEngine,
) : ViewModel() {
    private val _state = MutableStateFlow(ReviewState())
    val state: StateFlow<ReviewState> = _state.asStateFlow()

    fun load(username: String) {
        viewModelScope.launch {
            val games = repo.observeGames(username).first()
            _state.update { it.copy(games = games, selected = it.selected ?: games.firstOrNull()) }
        }
    }

    fun select(game: GameRecord) = _state.update {
        it.copy(selected = game, report = null, currentPly = 0, position = Position.START, lastMove = null, arrows = emptyList())
    }

    fun toggleFlip() = _state.update { it.copy(flipped = !it.flipped) }

    fun analyse(depth: Int = 16, multiPv: Int = 3) {
        val game = _state.value.selected ?: return
        viewModelScope.launch(Dispatchers.Default) {
            _state.update { it.copy(busy = true, progress = 0.01f, message = null, report = null) }
            runCatching {
                val evaluator = if (engine.isAvailable) engine else PgnEvaluator(game)
                if (!engine.isAvailable) {
                    _state.update {
                        it.copy(message = "Stockfish binary unavailable — using PGN/material estimates")
                    }
                }
                val report = GameReviewEngine.analyse(
                    game = game,
                    evaluator = evaluator,
                    depth = depth.coerceIn(12, 22),
                    multiPv = multiPv.coerceIn(1, 5),
                    maxPlies = 120,
                    onProgress = { progress -> _state.update { it.copy(progress = progress) } },
                )
                _state.update { it.copy(busy = false, progress = 1f, report = report) }
            }.onFailure { error ->
                _state.update {
                    it.copy(busy = false, message = "Analysis failed: ${error.message ?: "unknown error"}")
                }
            }
        }
    }

    fun jumpTo(ply: Int) {
        val game = _state.value.selected ?: return
        val (position, lastMove) = positionAt(game, ply)
        _state.update { it.copy(currentPly = ply, position = position, lastMove = lastMove, arrows = emptyList()) }
    }

    fun showBestLine(ply: Int) {
        val game = _state.value.selected ?: return
        val review = _state.value.report?.reviews?.firstOrNull { it.ply == ply } ?: return
        var position = positionAt(game, ply).first
        val arrows = mutableListOf<com.fredsystems.brilliantboard.ui.board.BoardArrow>()
        var lastMove: Pair<Int, Int>? = null
        review.bestLine.take(6).forEach { uci ->
            val move = position.parseUci(uci) ?: return@forEach
            if (!position.isLegal(move)) return@forEach
            arrows += com.fredsystems.brilliantboard.ui.board.BoardArrow(move.from, move.to, com.fredsystems.brilliantboard.ui.theme.Accent)
            lastMove = move.from to move.to
            position = position.apply(move)
        }
        _state.update { it.copy(position = position, lastMove = lastMove, arrows = arrows) }
    }

    private fun positionAt(game: GameRecord, ply: Int): Pair<Position, Pair<Int, Int>?> {
        var position = Position.START
        var last: Pair<Int, Int>? = null
        game.moves.take(ply).forEach { move ->
            val parsed = move.uci?.let { position.parseUci(it) } ?: position.parseSan(move.san)
            if (parsed != null && position.isLegal(parsed)) {
                last = parsed.from to parsed.to
                position = position.apply(parsed)
            }
        }
        return position to last
    }
}

data class SandboxState(
    val position: Position = Position.START,
    val flipped: Boolean = false,
    val lastMove: Pair<Int, Int>? = null,
    val fenInput: String = "",
    val pgnInput: String = "",
    val winPercent: Float? = null,
    val engineAvailable: Boolean = false,
    val message: String? = null,
    val explorerInfo: String = "",
)

class SandboxViewModel(
    private val engine: StockfishEngine,
    private val parser: com.fredsystems.brilliantboard.data.scraper.PgnParser,
    private val explorer: ExplorerApi,
    private val sounds: com.fredsystems.brilliantboard.util.MoveSounds,
) : ViewModel() {
    private val _state = MutableStateFlow(SandboxState(engineAvailable = engine.isAvailable))
    val state: StateFlow<SandboxState> = _state.asStateFlow()

    fun onMove(move: ChessMove) {
        sounds.play(_state.value.position.pieceAt(move.to) != '.')
        val next = _state.value.position.apply(move)
        _state.update { it.copy(position = next, lastMove = move.from to move.to, winPercent = null) }
    }

    fun setFen(value: String) = _state.update { it.copy(fenInput = value) }
    fun setPgn(value: String) = _state.update { it.copy(pgnInput = value) }
    fun setExplorerInfo(value: String) = _state.update { it.copy(explorerInfo = value) }

    suspend fun explorerSuggestions(fen: String) = explorer.suggestions(fen)
    fun toggleFlip() = _state.update { it.copy(flipped = !it.flipped) }
    fun reset() = _state.update {
        it.copy(position = Position.START, lastMove = null, winPercent = null, message = null)
    }

    fun loadFen() = viewModelScope.launch {
        runCatching {
            val position = Position.fromFen(_state.value.fenInput.trim())
            _state.update { it.copy(position = position, lastMove = null, message = "Position loaded") }
        }.onFailure { _state.update { it.copy(message = "Invalid FEN") } }
    }

    fun loadPgn() = viewModelScope.launch {
        val game = parser.parse(_state.value.pgnInput, Platform.LICHESS) ?: run {
            _state.update { it.copy(message = "Could not parse that PGN") }
            return@launch
        }
        var position = Position.START
        var last: Pair<Int, Int>? = null
        game.moves.forEach { move ->
            val parsed = move.uci?.let { position.parseUci(it) } ?: position.parseSan(move.san)
            if (parsed != null && position.isLegal(parsed)) {
                last = parsed.from to parsed.to
                position = position.apply(parsed)
            }
        }
        _state.update { it.copy(position = position, lastMove = last, message = "Loaded ${game.moves.size} moves") }
    }

    fun evaluate() = viewModelScope.launch {
        if (!engine.isAvailable) {
            _state.update { it.copy(message = "Bundle libstockfish.so to enable local analysis") }
            return@launch
        }
        runCatching {
            val position = _state.value.position
            val lines = engine.evaluate(position.fen(), emptyList(), 18, 3)
            val best = lines.firstOrNull()
            val win = WinProbability.from(best?.cp, best?.mate)?.let {
                if (position.whiteToMove) it else 100f - it
            }
            _state.update { it.copy(winPercent = win) }
        }.onFailure { _state.update { it.copy(message = it.message) } }
    }
}

data class SettingsState(val settings: com.fredsystems.brilliantboard.data.repository.AppSettings = com.fredsystems.brilliantboard.data.repository.AppSettings())

class SettingsViewModel(private val repository: SettingsRepository) : ViewModel() {
    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.settings.collect { _state.update { s -> s.copy(settings = it) } }
        }
    }

    fun setTheme(theme: String) = viewModelScope.launch { repository.setBoardTheme(theme) }
    fun setPieceSet(set: String) = viewModelScope.launch { repository.setPieceSet(set) }
    fun setBoardScale(scale: Float) = viewModelScope.launch { repository.setBoardScale(scale) }
    fun setSound(enabled: Boolean) = viewModelScope.launch { repository.setSound(enabled) }
    fun setDepth(depth: Int) = viewModelScope.launch { repository.setDepth(depth) }
    fun setDynamic(enabled: Boolean) = viewModelScope.launch { repository.setDynamicColor(enabled) }
    fun setAutoUpdates(enabled: Boolean) = viewModelScope.launch { repository.setAutoUpdates(enabled) }
}
