package com.myanmar.warpvpn

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppModel(
    val appName: String,
    val packageName: String,
    val icon: Drawable,
    var isExcluded: Boolean = false
)

class AppListActivity : AppCompatActivity() {

    private lateinit var rvApps: RecyclerView
    private lateinit var btnSave: MaterialButton
    private lateinit var btnBack: ImageView
    private lateinit var etSearchApp: EditText
    private lateinit var progressBar: ProgressBar

    private val fullAppList = mutableListOf<AppModel>()
    private val filteredAppList = mutableListOf<AppModel>()
    private lateinit var adapter: AppAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_list)

        btnBack = findViewById(R.id.btnBack)
        rvApps = findViewById(R.id.rvApps)
        btnSave = findViewById(R.id.btnSave)
        etSearchApp = findViewById(R.id.etSearchApp)
        progressBar = findViewById(R.id.progressBar)

        rvApps.layoutManager = LinearLayoutManager(this)

        btnBack.setOnClickListener {
            finish()
        }

        btnSave.setOnClickListener {
            saveExcludedApps()
            finish()
        }
        
        etSearchApp.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterApps(s.toString())
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        loadInstalledApps()
    }

    private fun filterApps(query: String) {
        val cleanQuery = query.trim().lowercase()
        filteredAppList.clear()

        if (cleanQuery.isEmpty()) {
            filteredAppList.addAll(fullAppList)
        } else {
            for (app in fullAppList) {
                if (app.appName.lowercase().contains(cleanQuery) ||
                    app.packageName.lowercase().contains(cleanQuery)
                ) {
                    filteredAppList.add(app)
                }
            }
        }

        if (::adapter.isInitialized) {
            adapter.notifyDataSetChanged()
        }
    }

    private fun loadInstalledApps() {
        progressBar.visibility = View.VISIBLE
        CoroutineScope(Dispatchers.IO).launch {
            val pm = packageManager
            val prefs = getSharedPreferences("WARP_VPN_PREFS", Context.MODE_PRIVATE)
            val excludedSet = prefs.getStringSet("EXCLUDED_APPS", emptySet()) ?: emptySet()

            val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            val tempInfoList = mutableListOf<AppModel>()

            for (appInfo in installedApps) {
                if ((appInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0 ||
                    appInfo.packageName == "com.google.android.youtube"
                ) {
                    val appName = pm.getApplicationLabel(appInfo).toString()
                    val icon = pm.getApplicationIcon(appInfo)
                    val isExcluded = excludedSet.contains(appInfo.packageName)

                    tempInfoList.add(AppModel(appName, appInfo.packageName, icon, isExcluded))
                }
            }

            tempInfoList.sortBy { it.appName.lowercase() }

            withContext(Dispatchers.Main) {
                fullAppList.clear()
                fullAppList.addAll(tempInfoList)

                filteredAppList.clear()
                filteredAppList.addAll(fullAppList)

                adapter = AppAdapter(filteredAppList)
                rvApps.adapter = adapter
                progressBar.visibility = View.GONE
            }
        }
    }

    private fun saveExcludedApps() {
        val excludedSet = fullAppList.filter { it.isExcluded }.map { it.packageName }.toSet()
        val prefs = getSharedPreferences("WARP_VPN_PREFS", Context.MODE_PRIVATE)
        prefs.edit().putStringSet("EXCLUDED_APPS", excludedSet).apply()
    }

    class AppAdapter(private val list: List<AppModel>) :
        RecyclerView.Adapter<AppAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val imgIcon: ImageView = view.findViewById(R.id.imgAppIcon)
            val tvName: TextView = view.findViewById(R.id.tvAppName)
            val tvPackage: TextView = view.findViewById(R.id.tvPackageName)
            val cbExclude: CheckBox = view.findViewById(R.id.cbExclude)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view =
                LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = list[position]
            holder.tvName.text = item.appName
            holder.tvPackage.text = item.packageName
            holder.imgIcon.setImageDrawable(item.icon)
            
            holder.cbExclude.setOnCheckedChangeListener(null)
            holder.cbExclude.isChecked = item.isExcluded

            holder.itemView.setOnClickListener {
                item.isExcluded = !item.isExcluded
                holder.cbExclude.isChecked = item.isExcluded
            }

            holder.cbExclude.setOnCheckedChangeListener { _, isChecked ->
                item.isExcluded = isChecked
            }
        }

        override fun getItemCount(): Int = list.size
    }
}
