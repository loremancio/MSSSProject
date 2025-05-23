package it.dii.unipi.myapplication.ui.screens.user

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import it.dii.unipi.myapplication.R
import it.dii.unipi.myapplication.app.Config
import it.dii.unipi.myapplication.database.CompensationDatabaseHelper
import it.dii.unipi.myapplication.model.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import kotlin.math.roundToInt

class UserProfileScreen : Fragment() {

    companion object {
        private const val TAG = "UserProfileScreen"
        private const val BASE_URL = Config.BASE_URL + "/profile"
    }

    data class Achievement(val title: String, val description: String)
    data class UserAchievements(val username: String, val achievements: List<Achievement>, val exposure_high: Int, val exposure_low: Int)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_user_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fetchUserSummary(view)
        // Setup compensation factor edit button
        val btnEditCompensation = view.findViewById<Button>(R.id.btnEditCompensation)
        btnEditCompensation.setOnClickListener {
            showCompensationDialog()
        }
    }

    /**
     * Shows a dialog to edit and save the compensation factor.
     */
    private fun showCompensationDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_compensation, null)
        val editText   = dialogView.findViewById<EditText>(R.id.etCompensation)
        val btnCancel  = dialogView.findViewById<MaterialButton>(R.id.btnCancel)
        val btnOk      = dialogView.findViewById<MaterialButton>(R.id.btnOkCustom)

        val dbHelper = CompensationDatabaseHelper(requireContext())
        val currentValue = dbHelper.getCompensationValue()
        if (currentValue != null) editText.setText(currentValue.toString())

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(true)
            .create()

        btnCancel.setOnClickListener { dialog.dismiss() }
        btnOk.setOnClickListener {
            val input = editText.text.toString()
            try {
                val value = input.toFloat()
                dbHelper.saveCompensationValue(value)
                dialog.dismiss()
            } catch (e: NumberFormatException) {
                editText.error = "Invalid value"
            }
        }
        dialog.show()
    }

    private fun fetchUserSummary(view: View) {
        val client = OkHttpClient()
        val baseUrl = BASE_URL
        val httpUrl = baseUrl
            .toHttpUrlOrNull()
            ?.newBuilder()
            ?.addQueryParameter("username", SessionManager(requireContext()).getUsernameFromSession())
            ?.build()
            ?: throw Exception("URL non valido: $baseUrl")

        val request = Request.Builder()
            .url(httpUrl)
            .get()
            .addHeader("Cookie", SessionManager(requireContext()).getCookieFromSession())
            .build()

        lifecycleScope.launch {
            try {
                val userAchievements = withContext(Dispatchers.IO) {
                    val response = client.newCall(request).execute()
                    if (!response.isSuccessful) throw Exception("Errore HTTP: ${response.code}")
                    val body = response.body?.string() ?: throw Exception("Empty Body")

                    val json = JSONObject(body)
                    val username = json.getString("username")
                    val achievementsJson = json.getJSONArray("achievements")
                    val exposure_high = json.getString("exposure_high").toDouble().roundToInt()
                    val exposure_low = json.getString("exposure_low").toDouble().roundToInt()

                    val achievements = mutableListOf<Achievement>()
                    for (i in 0 until achievementsJson.length()) {
                        val a = achievementsJson.getJSONObject(i)
                        achievements.add(Achievement(a.getString("title"), a.getString("description")))
                    }

                    UserAchievements(username, achievements, exposure_high, exposure_low)
                }

                withContext(Dispatchers.Main) {
                    view.findViewById<TextView>(R.id.tvUserName).text = userAchievements.username + "'s Profile"

                    val badgeData = listOf(
                        Triple("City Explorer", R.id.iconBadgeCities to R.id.countTextViewCities, R.id.blockBadgeCities),
                        Triple("World Traveler", R.id.iconBadgeCountries to R.id.countTextViewCountries, R.id.blockBadgeCountries),
                        Triple("Measurement Master", R.id.iconBadgeRegistrations to R.id.countTextViewRegistered, R.id.blockBadgeRegistrations)
                    )

                    for ((title, ids, layoutId) in badgeData) {
                        val (iconId, textId) = ids
                        val match = userAchievements.achievements.firstOrNull { it.title == title }

                        val blockLayout = view.findViewById<androidx.cardview.widget.CardView>(layoutId)

                        if (match != null) {
                            view.findViewById<ImageView>(iconId).setImageResource(R.drawable.ic_badge_col)
                            view.findViewById<TextView>(textId).text = match.description
                            blockLayout.visibility = View.VISIBLE
                        } else {
                            blockLayout.visibility = View.GONE
                        }
                    }

                    view.findViewById<TextView>(R.id.textExpositionHigh).text = "You have been exposed to high noise for " + userAchievements.exposure_high + " seconds"
                    view.findViewById<TextView>(R.id.textExpositionLow).text = "You have been exposed to low noise for " + userAchievements.exposure_low + " seconds"
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error on request OkHttp", e)
            }
        }
    }
}
