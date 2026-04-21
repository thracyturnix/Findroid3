package dev.jdtech.jellyfin.presentation.film

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jdtech.jellyfin.core.presentation.dummy.dummyCollections
import dev.jdtech.jellyfin.core.presentation.dummy.dummyHomeSection
import dev.jdtech.jellyfin.core.presentation.dummy.dummyHomeView
import dev.jdtech.jellyfin.core.presentation.dummy.dummyServer
import dev.jdtech.jellyfin.film.presentation.home.HomeAction
import dev.jdtech.jellyfin.film.presentation.home.HomeState
import dev.jdtech.jellyfin.film.presentation.home.HomeViewModel
import dev.jdtech.jellyfin.models.FindroidCollection
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.presentation.components.ErrorDialog
import dev.jdtech.jellyfin.presentation.film.components.HomeHeader
import dev.jdtech.jellyfin.presentation.film.components.HomeLibrariesGrid
import dev.jdtech.jellyfin.presentation.film.components.HomeSection
import dev.jdtech.jellyfin.presentation.film.components.HomeSectionsBottomSheet
import dev.jdtech.jellyfin.presentation.film.components.HomeView
import dev.jdtech.jellyfin.presentation.film.components.ServerSelectionBottomSheet
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.presentation.utils.rememberSafePadding
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    onLibraryClick: (library: FindroidCollection) -> Unit,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onManageServers: () -> Unit,
    onItemClick: (item: FindroidItem) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(true) { viewModel.loadData(loadSuggestions = false) }

    HomeScreenLayout(
        state = state,
        onAction = { action ->
            when (action) {
                is HomeAction.OnItemClick -> onItemClick(action.item)
                is HomeAction.OnLibraryClick -> onLibraryClick(action.library)
                is HomeAction.OnSearchClick -> onSearchClick()
                is HomeAction.OnSettingsClick -> onSettingsClick()
                is HomeAction.OnManageServers -> onManageServers()
                else -> Unit
            }
            viewModel.onAction(action)
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreenLayout(state: HomeState, onAction: (HomeAction) -> Unit) {
    val scope = rememberCoroutineScope()
    val safePadding = rememberSafePadding(handleStartInsets = false)

    val paddingStart = safePadding.start + MaterialTheme.spacings.default
    val paddingTop = safePadding.top + MaterialTheme.spacings.small
    val paddingEnd = safePadding.end + MaterialTheme.spacings.default
    val paddingBottom = safePadding.bottom + MaterialTheme.spacings.default

    val itemsPadding = PaddingValues(start = paddingStart, end = paddingEnd)

    val contentPaddingTop = safePadding.top + 88.dp

    var showErrorDialog by rememberSaveable { mutableStateOf(false) }
    val showServerSelectionSheetState = rememberModalBottomSheetState()
    var showServerSelectionBottomSheet by remember { mutableStateOf(false) }
    var showSectionSelectionBottomSheet by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().semantics { isTraversalGroup = true }) {
        PullToRefreshBox(isRefreshing = false, onRefresh = { onAction(HomeAction.OnRetryClick) }) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().semantics { traversalIndex = 1f },
                contentPadding = PaddingValues(top = contentPaddingTop, bottom = paddingBottom),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.medium),
            ) {
                if (state.sectionVisibility.libraries) {
                    item(key = "libraries") {
                        HomeLibrariesGrid(
                            libraries = state.libraries,
                            selectedLibrary = state.selectedLibrary,
                            defaultStartLibraryId = state.defaultStartLibraryId,
                            itemsPadding = itemsPadding,
                            onAction = onAction,
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
                state.resumeSection?.takeIf { state.sectionVisibility.continueWatching }?.let { section ->
                    item(key = section.id) {
                        HomeSection(
                            section = section.homeSection,
                            itemsPadding = itemsPadding,
                            onAction = onAction,
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
                state.nextUpSection?.takeIf { state.sectionVisibility.nextUp }?.let { section ->
                    item(key = section.id) {
                        HomeSection(
                            section = section.homeSection,
                            itemsPadding = itemsPadding,
                            onAction = onAction,
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
                items(
                    state.views.filter {
                        state.sectionVisibility.latestByViewId[it.id] ?: true
                    },
                    key = { it.id },
                ) { view ->
                    HomeView(
                        view = view,
                        itemsPadding = itemsPadding,
                        onAction = onAction,
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }

        if (state.error != null && showErrorDialog) {
            ErrorDialog(exception = state.error!!, onDismissRequest = { showErrorDialog = false })
        }
    }

    HomeHeader(
        serverName = state.server?.name ?: "",
        isLoading = state.isLoading,
        isError = state.error != null,
        onServerClick = { showServerSelectionBottomSheet = true },
        onErrorClick = { showErrorDialog = true },
        onRetryClick = { onAction(HomeAction.OnRetryClick) },
        onSectionsClick = { showSectionSelectionBottomSheet = true },
        onSearchClick = { onAction(HomeAction.OnSearchClick) },
        onUserClick = { onAction(HomeAction.OnSettingsClick) },
        modifier = Modifier.padding(start = paddingStart, top = paddingTop, end = paddingEnd),
    )

    if (showServerSelectionBottomSheet) {
        ServerSelectionBottomSheet(
            currentServerId = state.server?.id ?: "",
            onUpdate = {
                onAction(HomeAction.OnRetryClick)
                scope
                    .launch { showServerSelectionSheetState.hide() }
                    .invokeOnCompletion {
                        if (!showServerSelectionSheetState.isVisible) {
                            showServerSelectionBottomSheet = false
                        }
                    }
            },
            onManage = {
                onAction(HomeAction.OnManageServers)
                scope.launch { showServerSelectionSheetState.hide() }
            },
            onDismissRequest = { showServerSelectionBottomSheet = false },
            sheetState = showServerSelectionSheetState,
        )
    }

    if (showSectionSelectionBottomSheet) {
        HomeSectionsBottomSheet(
            librariesVisible = state.sectionVisibility.libraries,
            continueWatchingVisible = state.sectionVisibility.continueWatching,
            nextUpVisible = state.sectionVisibility.nextUp,
            views = state.views,
            latestVisibility = state.sectionVisibility.latestByViewId,
            onLibrariesVisibilityChange = {
                onAction(HomeAction.SetLibrariesVisibility(it))
            },
            onContinueWatchingVisibilityChange = {
                onAction(HomeAction.SetContinueWatchingVisibility(it))
            },
            onNextUpVisibilityChange = {
                onAction(HomeAction.SetNextUpVisibility(it))
            },
            onLatestVisibilityChange = { view, visible ->
                onAction(HomeAction.SetLatestVisibility(view.id, visible))
            },
            onDismissRequest = { showSectionSelectionBottomSheet = false },
        )
    }
}

@PreviewScreenSizes
@Composable
private fun HomeScreenLayoutPreview() {
    FindroidTheme {
        HomeScreenLayout(
            state =
                HomeState(
                    server = dummyServer,
                    libraries = dummyCollections,
                    defaultStartLibraryId = dummyCollections.firstOrNull()?.id?.toString(),
                    resumeSection = dummyHomeSection,
                    views = listOf(dummyHomeView),
                    error = Exception("Failed to load data"),
                ),
            onAction = {},
        )
    }
}
