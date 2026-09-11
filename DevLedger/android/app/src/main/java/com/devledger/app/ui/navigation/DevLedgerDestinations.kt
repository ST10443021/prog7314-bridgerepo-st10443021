package com.devledger.app.ui.navigation

/** Route constants matching the navigation wireflow diagram (Figure 11) from the
 *  Part 1 Planning and Design document. */
object DevLedgerDestinations {
    const val SPLASH = "splash"
    const val AUTH = "auth"
    const val BIOMETRIC = "biometric"
    const val DASHBOARD = "dashboard"
    const val SNIPPET_VAULT = "snippet_vault"
    const val SNIPPET_EDITOR = "snippet_editor?snippetId={snippetId}"
    const val SNIPPET_DETAIL = "snippet_detail/{snippetId}"
    const val FINANCE = "finance"
    const val ADD_TRANSACTION = "add_transaction"
    const val NOTIFICATIONS = "notifications"
    const val SETTINGS = "settings"
    const val REGISTER = "register"
    const val TERMS = "terms"
    const val PRIVACY_POLICY = "privacy_policy"

    fun snippetEditorRoute(snippetId: String? = null) =
        if (snippetId == null) "snippet_editor" else "snippet_editor?snippetId=$snippetId"

    fun snippetDetailRoute(snippetId: String) = "snippet_detail/$snippetId"
}
