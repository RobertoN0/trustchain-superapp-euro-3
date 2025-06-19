package nl.tudelft.trustchain.eurotoken.offlinePayment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import nl.tudelft.trustchain.eurotoken.entity.BillFaceToken
import nl.tudelft.trustchain.eurotoken.offlinePayment.tokenSelection.SelectionResult
import nl.tudelft.trustchain.eurotoken.offlinePayment.tokenSelection.SelectionStrategy
import nl.tudelft.trustchain.eurotoken.offlinePayment.tokenSelection.strategies.DoubleSpendSelector
import nl.tudelft.trustchain.eurotoken.offlinePayment.tokenSelection.strategies.ForgedTokenSelector
import nl.tudelft.trustchain.eurotoken.offlinePayment.tokenSelection.strategies.MPTSelection

class TokenSelectionViewModel(
    private val tokenStore: ITokenStore
): ViewModel() {
    private val _error = MutableSharedFlow<String>()
    val error = _error.asSharedFlow()

    private val _selectedTokens = MutableSharedFlow<List<BillFaceToken>>()
    val selectedTokens = _selectedTokens.asSharedFlow()

    fun selectDoubleSpending(amount: Long) {
        val selector = DoubleSpendSelector(tokenStore)
        selectTokens(selector, amount)
    }

    fun selectMPT(amount: Long, seed: String) {
        val selector = MPTSelection(tokenStore, seed)
        selectTokens(selector, amount)
    }

    fun selectForged(amount: Long) {
        val selector = ForgedTokenSelector(tokenStore)
        selectTokens(selector, amount)
    }

    private fun selectTokens(
        selector: SelectionStrategy,
        amount: Long
    ) {
        viewModelScope.launch {
            when (val result = selector.select(amount)) {
                is SelectionResult.Failure -> _error.emit(result.reason)
                is SelectionResult.Success -> _selectedTokens.emit(result.tokens)
            }
        }

    }
}

class TokenSelectionViewModelFactory(
    private val tokenStore: ITokenStore
): ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TokenSelectionViewModel::class.java)) {
            return TokenSelectionViewModel(tokenStore) as T
        }
        throw IllegalStateException("Unknown ViewModel class")
    }
}
