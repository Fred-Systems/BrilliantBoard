package com.fredsystems.brilliantboard.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fredsystems.brilliantboard.domain.model.Platform
import com.fredsystems.brilliantboard.ui.SearchViewModel
import com.fredsystems.brilliantboard.ui.theme.SurfaceRaised

@Composable
fun UsernameSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    searchViewModel: SearchViewModel,
    onSelected: (String, Platform?) -> Unit,
) {
    val searchState by searchViewModel.state.collectAsState()
    Column(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                searchViewModel.search(it)
            },
            label = { Text(label) },
            singleLine = true,
            trailingIcon = {
                if (searchState.searching) CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    modifier = Modifier.padding(4.dp),
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )
        if (searchState.suggestions.isNotEmpty()) {
            Card(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceRaised),
            ) {
                LazyColumn(Modifier.fillMaxWidth()) {
                    items(searchState.suggestions) { suggestion ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onValueChange(suggestion.username)
                                    onSelected(suggestion.username, suggestion.platform)
                                    searchViewModel.clear()
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                suggestion.username,
                                Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            suggestion.rating?.let {
                                Text("$it", style = MaterialTheme.typography.bodySmall)
                            }
                            Text(
                                "${suggestion.source} · ${suggestion.platform.label}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
