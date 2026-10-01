package com.example.dbviewer.data
import android.content.Context
import android.net.Uri
class DbRepository(private val context: Context) { suspend fun open(uri: Uri) = DbSession.open(context, uri) }
