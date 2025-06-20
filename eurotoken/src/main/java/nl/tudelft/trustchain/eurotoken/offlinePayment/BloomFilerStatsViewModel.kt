package nl.tudelft.trustchain.eurotoken.offlinePayment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.tudelft.trustchain.eurotoken.entity.BFSpentMoniesManager

class BloomFilerStatsViewModel(
    private val bfManager: BFSpentMoniesManager
): ViewModel() {
    private val _receivedCount = MutableStateFlow(0)
    val receivedCount: StateFlow<Int> = _receivedCount

    private val _capacityUsed = MutableStateFlow(0)
    val capacityUsed: StateFlow<Int> = _capacityUsed

    init {
        viewModelScope.launch {
            bfManager.bloomFilter.collect { bf ->
                _capacityUsed.update { bf.estimateSize() }
            }
        }
        viewModelScope.launch {
            bfManager.receivedCount.collect {count ->
                _receivedCount.update { count }
            }
        }
    }
}

class BloomFilterViewModelFactory(
    private val bfManager: BFSpentMoniesManager
): ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BloomFilerStatsViewModel::class.java)) {
            return BloomFilerStatsViewModel(bfManager) as T
        }
        throw IllegalStateException("Unknown ViewModel class")
    }
}
