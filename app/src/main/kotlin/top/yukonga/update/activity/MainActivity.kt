package top.yukonga.update.activity

import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.Html
import android.text.InputType
import android.text.TextWatcher
import android.text.method.LinkMovementMethod
import android.view.View.OnFocusChangeListener
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import top.yukonga.miuiStringToast.MiuiStringToast.showStringToast
import top.yukonga.update.BuildConfig
import top.yukonga.update.R
import top.yukonga.update.activity.adapter.CustomArrayAdapter
import top.yukonga.update.activity.viewModel.MainViewModel
import top.yukonga.update.databinding.ActivityMainBinding
import top.yukonga.update.databinding.MainContentBinding
import top.yukonga.update.logic.data.DeviceInfoHelper
import top.yukonga.update.logic.data.RomInfoHelper
import top.yukonga.update.logic.utils.AnimUtils.fadInAnimation
import top.yukonga.update.logic.utils.AnimUtils.fadOutAnimation
import top.yukonga.update.logic.utils.AnimUtils.setTextAnimation
import top.yukonga.update.logic.utils.AppUtils
import top.yukonga.update.logic.utils.AppUtils.addInsetsByMargin
import top.yukonga.update.logic.utils.AppUtils.addInsetsByPadding
import top.yukonga.update.logic.utils.AppUtils.dp
import top.yukonga.update.logic.utils.AppUtils.hideKeyBoard
import top.yukonga.update.logic.utils.AppUtils.json
import top.yukonga.update.logic.utils.AppUtils.setCopyClickListener
import top.yukonga.update.logic.utils.AppUtils.setDownloadClickListener
import top.yukonga.update.logic.utils.FileUtils
import top.yukonga.update.logic.utils.HapticUtils.hapticConfirm
import top.yukonga.update.logic.utils.HapticUtils.hapticReject
import top.yukonga.update.logic.utils.InfoUtils.getRecoveryRomInfo
import top.yukonga.update.logic.utils.LoginUtils

class MainActivity : AppCompatActivity() {

    // Start ViewBinding.
    private lateinit var _activityMainBinding: ActivityMainBinding
    private val activityMainBinding get() = _activityMainBinding
    private val mainContentBinding: MainContentBinding get() = activityMainBinding.mainContent

    private lateinit var prefs: SharedPreferences

    private lateinit var codeNameWatcher: TextWatcher
    private lateinit var deviceNameWatcher: TextWatcher

    private lateinit var mainViewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Get SharedPreferences.
        prefs = PreferenceManager.getDefaultSharedPreferences(this@MainActivity)

        // Get ViewModel.
        mainViewModel = ViewModelProvider(this@MainActivity)[MainViewModel::class.java]

        // Inflate view.
        inflateView()

        // Enable edge to edge.
        setupEdgeToEdge()

        // Setup Cutout mode.
        setupCutoutMode()

        // Setup main information.
        setupMainInformation()

        // Setup TopAppBar.
        setupTopAppBar()

        // Check if logged in.
        checkIfLoggedIn()
    }

    override fun onResume() {
        super.onResume()

        mainContentBinding.apply {

            // Setup Fab OnClickListener.
            activityMainBinding.implement.setOnClickListener {
                hapticConfirm(activityMainBinding.implement)

                val deviceRegion = textFields.deviceRegion.editText?.text.toString()
                val codeName = textFields.codeName.editText?.text.toString()
                val deviceName = textFields.deviceName.editText?.text.toString()

                val regionCode = DeviceInfoHelper.regionCode(deviceRegion)
                val regionNameExt = DeviceInfoHelper.regionNameExt(deviceRegion)
                val codeNameExt = codeName + regionNameExt

                val androidVersion = textFields.androidVersion.editText?.text.toString()
                val systemVersion = textFields.systemVersion.editText?.text.toString()

                val deviceCode = DeviceInfoHelper.deviceCode(androidVersion, codeName, regionCode)
                val systemVersionTextExt = systemVersion.uppercase().replace("OS1", "V816").replace("AUTO", deviceCode)

                val branchExt = if (systemVersion.contains(".DEV")) "X" else "F"

                // Acquire ROM info.
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val recoveryRomInfo = json.decodeFromString<RomInfoHelper.RomInfo>(
                            getRecoveryRomInfo(
                                this@MainActivity, branchExt, codeNameExt, regionCode, systemVersionTextExt, androidVersion
                            )
                        )

                        withContext(Dispatchers.Main) {
                            prefs.edit().putString("deviceName", deviceName).putString("codeName", codeName).putString("deviceRegion", deviceRegion)
                                .putString("systemVersion", systemVersion).putString("androidVersion", androidVersion).apply()

                            // Hide all cardViews & Show a toast if we didn't get anything from request.
                            if (recoveryRomInfo.currentRom?.bigversion == null && recoveryRomInfo.incrementRom?.bigversion == null) {
                                mainViewModel.apply {
                                    type = null
                                    device = null
                                    version = null
                                    codebase = null
                                    branch = null
                                    filename = null
                                    filesize = null
                                    bigversion = null
                                    officialDownload = null
                                    cdn1Download = null
                                    cdn2Download = null
                                    changelog = null
                                    typeIncrement = null
                                    deviceIncrement = null
                                    versionIncrement = null
                                    codebaseIncrement = null
                                    branchIncrement = null
                                    filenameIncrement = null
                                    filesizeIncrement = null
                                    bigversionIncrement = null
                                    officialDownloadIncrement = null
                                    cdn1DownloadIncrement = null
                                    cdn2DownloadIncrement = null
                                    changelogIncrement = null
                                }
                                setupCardViews()
                                showStringToast(this@MainActivity, getString(R.string.toast_no_info), 0)
                                throw NoSuchFieldException()
                            }

                            if (recoveryRomInfo.currentRom?.bigversion == null) {
                                showStringToast(this@MainActivity, getString(R.string.toast_wrong_info), 0)
                            }

                            // Show a toast if we detect that the login has expired.
                            if (FileUtils.isCookiesFileExists(this@MainActivity)) {
                                val cookiesFile = FileUtils.readCookiesFile(this@MainActivity)
                                val cookies = json.decodeFromString<MutableMap<String, String>>(cookiesFile)
                                val description = cookies["description"].toString()
                                val authResult = cookies["authResult"].toString()
                                if (description.isNotEmpty() && recoveryRomInfo.authResult != 1 && authResult != "-1") {
                                    loginCard.loginIcon.setImageResource(R.drawable.ic_error)
                                    loginCard.loginTitle.text = getString(R.string.login_expired)
                                    loginCard.loginDesc.text = getString(R.string.login_expired_desc)
                                    cookies.clear()
                                    cookies["authResult"] = "-1"
                                    FileUtils.saveCookiesFile(this@MainActivity, Json.encodeToString(cookies))
                                    if (prefs.getString("auto_login", "") == "1") {
                                        showStringToast(this@MainActivity, getString(R.string.login_expired_auto), 1)
                                        LoginUtils().login(
                                            this@MainActivity,
                                            LoginUtils().getAccountAndPassword(this@MainActivity).first,
                                            LoginUtils().getAccountAndPassword(this@MainActivity).second,
                                            prefs.getString("global", "") ?: "0",
                                            prefs.getString("save_password", "") ?: "0",
                                            true
                                        )
                                    } else {
                                        showStringToast(this@MainActivity, getString(R.string.login_expired_dialog), 0)
                                        activityMainBinding.apply {
                                            toolbar.menu.findItem(R.id.login).isVisible = true
                                            toolbar.menu.findItem(R.id.logout).isVisible = false
                                        }
                                    }
                                }
                            }

                            if (recoveryRomInfo.currentRom?.bigversion != null) {
                                val log = StringBuilder()
                                recoveryRomInfo.currentRom.changelog!!.forEach {
                                    log.append(it.key).append("\n- ").append(it.value.txt.joinToString("\n- ")).append("\n\n")
                                }

                                mainViewModel.apply {
                                    type = recoveryRomInfo.currentRom.type?.uppercase()
                                    device = recoveryRomInfo.currentRom.device
                                    version = recoveryRomInfo.currentRom.version
                                    codebase = recoveryRomInfo.currentRom.codebase
                                    branch = recoveryRomInfo.currentRom.branch
                                    filename = recoveryRomInfo.currentRom.filename
                                    filesize = recoveryRomInfo.currentRom.filesize
                                    bigversion = if (recoveryRomInfo.currentRom.bigversion.contains("816")) {
                                        recoveryRomInfo.currentRom.bigversion.replace("816", "HyperOS 1.0")
                                    } else {
                                        "MIUI ${recoveryRomInfo.currentRom.bigversion}"
                                    }
                                    officialDownload = if (recoveryRomInfo.currentRom.md5 != recoveryRomInfo.latestRom?.md5) {
                                        withContext(Dispatchers.IO) {
                                            val recoveryRomInfoCurrent = json.decodeFromString<RomInfoHelper.RomInfo>(
                                                getRecoveryRomInfo(
                                                    this@MainActivity, "", codeNameExt, regionCode, systemVersionTextExt, androidVersion
                                                )
                                            )
                                            getString(
                                                R.string.official_link, recoveryRomInfoCurrent.currentRom?.version, recoveryRomInfoCurrent.latestRom?.filename
                                            )

                                        }
                                    } else {
                                        getString(R.string.official_link, recoveryRomInfo.currentRom.version, recoveryRomInfo.latestRom?.filename)
                                    }
                                    cdn1Download = getString(R.string.cdn1_link, recoveryRomInfo.currentRom.version, recoveryRomInfo.currentRom.filename)
                                    cdn2Download = getString(R.string.cdn2_link, recoveryRomInfo.currentRom.version, recoveryRomInfo.currentRom.filename)
                                    changelog = log.toString().trimEnd()
                                }
                            } else {
                                mainViewModel.apply {
                                    type = null
                                    device = null
                                    version = null
                                    codebase = null
                                    branch = null
                                    filename = null
                                    filesize = null
                                    bigversion = null
                                    officialDownload = null
                                    cdn1Download = null
                                    cdn2Download = null
                                    changelog = null
                                }
                            }

                            if (recoveryRomInfo.incrementRom?.bigversion != null) {
                                val incrementRomLog = StringBuilder()
                                recoveryRomInfo.incrementRom.changelog!!.forEach {
                                    incrementRomLog.append(it.key).append("\n- ").append(it.value.txt.joinToString("\n- ")).append("\n\n")
                                }

                                mainViewModel.apply {
                                    typeIncrement = recoveryRomInfo.incrementRom.type?.uppercase()
                                    deviceIncrement = recoveryRomInfo.incrementRom.device
                                    versionIncrement = recoveryRomInfo.incrementRom.version
                                    codebaseIncrement = recoveryRomInfo.incrementRom.codebase
                                    branchIncrement = recoveryRomInfo.incrementRom.branch
                                    filenameIncrement = recoveryRomInfo.incrementRom.filename.toString().substringBefore(".zip") + ".zip"
                                    filesizeIncrement = recoveryRomInfo.incrementRom.filesize
                                    bigversionIncrement = if (recoveryRomInfo.incrementRom.bigversion.contains("816")) {
                                        recoveryRomInfo.incrementRom.bigversion.replace("816", "HyperOS 1.0")
                                    } else {
                                        "MIUI ${recoveryRomInfo.incrementRom.bigversion}"
                                    }
                                    officialDownloadIncrement =
                                        getString(R.string.official_link, recoveryRomInfo.incrementRom.version, recoveryRomInfo.incrementRom.filename)
                                    cdn1DownloadIncrement =
                                        getString(R.string.cdn1_link, recoveryRomInfo.incrementRom.version, recoveryRomInfo.incrementRom.filename)
                                    cdn2DownloadIncrement =
                                        getString(R.string.cdn2_link, recoveryRomInfo.incrementRom.version, recoveryRomInfo.incrementRom.filename)
                                    changelogIncrement = incrementRomLog.toString().trimEnd()
                                }
                            } else {
                                mainViewModel.apply {
                                    typeIncrement = null
                                    deviceIncrement = null
                                    versionIncrement = null
                                    codebaseIncrement = null
                                    branchIncrement = null
                                    filenameIncrement = null
                                    filesizeIncrement = null
                                    bigversionIncrement = null
                                    officialDownloadIncrement = null
                                    cdn1DownloadIncrement = null
                                    cdn2DownloadIncrement = null
                                    changelogIncrement = null
                                }
                            }
                            setupCardViews()

                        } // Dispatchers.Main

                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                } // Coroutine

            } // Fab operation

        } // Main content

        // Hide all card views if we didn't get anything from request.
        setupCardViews()

    } // OnResume

    override fun onDestroy() {
        super.onDestroy()
        activityMainBinding
    }

    private fun showLoginDialog() {
        val view = createDialogView()
        val title = layoutInflater.inflate(R.layout.dialog_login, view, false)
        title.findViewById<MaterialCheckBox>(R.id.global).apply {
            isChecked = prefs.getString("global", "") == "1"
            setOnCheckedChangeListener { _, isChecked ->
                prefs.edit().putString("global", if (isChecked) "1" else "0").apply()
            }
        }
        val inputAccountLayout = createTextInputLayout(getString(R.string.account))
        val inputAccount = createTextInputEditText().apply {
            setText(LoginUtils().getAccountAndPassword(this@MainActivity).first)
        }
        inputAccountLayout.addView(inputAccount)
        val inputPasswordLayout = createTextInputLayout(getString(R.string.password), TextInputLayout.END_ICON_PASSWORD_TOGGLE)
        val inputPassword = createTextInputEditText(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD).apply {
            setText(LoginUtils().getAccountAndPassword(this@MainActivity).second)
        }
        inputPasswordLayout.addView(inputPassword)
        val savePasswordCheckBox =
            createCheckBox("save_password", R.string.save_password, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(23.dp, 0.dp, 0.dp, 0.dp)
            })
        val autoLoginCheckBox = createCheckBox("auto_login",
            R.string.auto_login,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, 0f).apply {
                setMargins(23.dp, 0.dp, 27.dp, 0.dp)
            })
        savePasswordCheckBox.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putString("save_password", if (isChecked) "1" else "0").apply()
            autoLoginCheckBox.isEnabled = isChecked
            if (!isChecked) autoLoginCheckBox.isChecked = false
        }
        autoLoginCheckBox.isEnabled = savePasswordCheckBox.isChecked
        autoLoginCheckBox.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putString("auto_login", if (isChecked) "1" else "0").apply()
        }
        val checkBoxLinearLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            addView(savePasswordCheckBox)
            addView(autoLoginCheckBox)
        }
        view.apply {
            addView(title)
            addView(inputAccountLayout)
            addView(inputPasswordLayout)
            addView(checkBoxLinearLayout)
        }
        MaterialAlertDialogBuilder(this@MainActivity).apply {
            setView(view)
            setNegativeButton(getString(R.string.cancel)) { dialog, _ ->
                hapticReject(activityMainBinding.toolbar)
                dialog.dismiss()
            }
            setPositiveButton(getString(R.string.login)) { _, _ ->
                hapticConfirm(activityMainBinding.toolbar)
                val global = prefs.getString("global", "") ?: "0"
                val savePassword = prefs.getString("save_password", "") ?: "0"
                val mInputAccount = inputAccount.text.toString()
                val mInputPassword = inputPassword.text.toString()
                lifecycleScope.launch(Dispatchers.IO) {
                    val isValid = LoginUtils().login(this@MainActivity, mInputAccount, mInputPassword, global, savePassword)
                    withContext(Dispatchers.Main) {
                        if (isValid) {
                            mainContentBinding.apply {
                                loginCard.loginIcon.setImageResource(R.drawable.ic_check_circle)
                                loginCard.loginTitle.text = getString(R.string.logged_in)
                                loginCard.loginDesc.text = getString(R.string.using_v2)
                            }
                            activityMainBinding.apply {
                                toolbar.menu.findItem(R.id.login).isVisible = false
                                toolbar.menu.findItem(R.id.logout).isVisible = true
                            }
                        }
                    }
                }
            }
        }.show()
    }

    private fun showLogoutDialog() {
        MaterialAlertDialogBuilder(this@MainActivity).apply {
            setTitle(getString(R.string.logout))
            setMessage(getString(R.string.logout_desc)).setNegativeButton(getString(R.string.cancel)) { dialog, _ ->
                hapticReject(activityMainBinding.toolbar)
                dialog.dismiss()
            }
            setPositiveButton(getString(R.string.confirm)) { _, _ ->
                hapticConfirm(activityMainBinding.toolbar)
                LoginUtils().logout(this@MainActivity)
                mainContentBinding.apply {
                    loginCard.loginIcon.setImageResource(R.drawable.ic_cancel)
                    loginCard.loginTitle.text = getString(R.string.no_account)
                    loginCard.loginDesc.text = getString(R.string.login_desc)
                }
                activityMainBinding.apply {
                    toolbar.menu.findItem(R.id.login).isVisible = true
                    toolbar.menu.findItem(R.id.logout).isVisible = false
                }
            }
        }.show()
    }

    private fun showAboutDialog() {
        val rootView = MaterialAlertDialogBuilder(this@MainActivity).setView(R.layout.dialog_about).show()
        val versionTextView = rootView.findViewById<TextView>(R.id.version)!!
        val githubSpannableTextView = rootView.findViewById<TextView>(R.id.github)!!

        versionTextView.text = getString(R.string.app_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE.toString())
        githubSpannableTextView.text = Html.fromHtml(getString(R.string.app_github), Html.FROM_HTML_MODE_COMPACT)
        githubSpannableTextView.movementMethod = LinkMovementMethod.getInstance()
    }

    private fun setupEdgeToEdge() {
        // Enable edge to edge
        enableEdgeToEdge()
        if (AppUtils.atLeast(Build.VERSION_CODES.Q)) window.isNavigationBarContrastEnforced = false

        // Add insets
        activityMainBinding.root.addInsetsByPadding(top = true)
        activityMainBinding.appBarLayout.addInsetsByPadding(left = true, right = true)
        activityMainBinding.implement.addInsetsByMargin(bottom = true, left = true, right = true)
        mainContentBinding.scrollView.addInsetsByPadding(left = true, right = true, bottom = true)

        // Add animation for software keyboard
        ViewCompat.setWindowInsetsAnimationCallback(
            activityMainBinding.implement, object : WindowInsetsAnimationCompat.Callback(DISPATCH_MODE_CONTINUE_ON_SUBTREE) {
                override fun onProgress(
                    insets: WindowInsetsCompat,
                    runningAnimations: List<WindowInsetsAnimationCompat?>
                ): WindowInsetsCompat {
                    val insetSystemBars = Insets.max(
                        insets.getInsets(WindowInsetsCompat.Type.systemBars()),
                        insets.getInsets(WindowInsetsCompat.Type.displayCutout())
                    )
                    val insetIme = insets.getInsets(WindowInsetsCompat.Type.ime())
                    val diff = Insets.subtract(insetSystemBars, insetIme)
                    activityMainBinding.implement.translationY = if ( diff.bottom > 0 ) 0f else diff.bottom.toFloat()

                    // Show fab when click on text fields
                    if (insets.isVisible(WindowInsetsCompat.Type.ime())) {
                        activityMainBinding.implement.apply { show(); shrink() }
                    }

                    return insets
                }

            }
        )

        // Hide fab and software keyboard when scrolling
        mainContentBinding.scrollView.setOnScrollChangeListener { view, _, scrollY, _, oldScrollY ->
            when {
                scrollY > oldScrollY -> {
                    hideKeyBoard(this@MainActivity, view)
                    activityMainBinding.implement.hide()
                }
                else -> activityMainBinding.implement.show()
            }
        }
    }

    private fun setupCutoutMode() {
        if (AppUtils.atLeast(Build.VERSION_CODES.P)) {
            val layoutParam = window.attributes
            layoutParam.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.setAttributes(layoutParam)
        }
    }

    private fun inflateView() {
        _activityMainBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(activityMainBinding.root)
    }

    private fun setupMainInformation() {
        mainContentBinding.apply {

            // Hide input method when focus is on dropdown.
            textFields.deviceRegionDropdown.onFocusChangeListener = OnFocusChangeListener { view, hasFocus ->
                if (hasFocus) hideKeyBoard(this@MainActivity, view)
            }
            textFields.androidVersionDropdown.onFocusChangeListener = OnFocusChangeListener { view, hasFocus ->
                if (hasFocus) hideKeyBoard(this@MainActivity, view)
            }

            // Setup default device information.
            textFields.deviceName.editText!!.setText(prefs.getString("deviceName", ""))
            textFields.codeName.editText!!.setText(prefs.getString("codeName", ""))
            textFields.deviceRegion.editText!!.setText(prefs.getString("deviceRegion", ""))
            textFields.systemVersion.editText!!.setText(prefs.getString("systemVersion", ""))
            textFields.androidVersion.editText!!.setText(prefs.getString("androidVersion", ""))

            // Setup DropDownList.
            val deviceNamesAdapter = CustomArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, DeviceInfoHelper.deviceNames)
            val codeNamesAdapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, DeviceInfoHelper.codeNames)
            val deviceRegionAdapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, DeviceInfoHelper.regionNames)
            val androidVersionAdapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, DeviceInfoHelper.androidVersions)
            (textFields.deviceName.editText as? MaterialAutoCompleteTextView)?.apply {
                setAdapter(deviceNamesAdapter)
                setDropDownBackgroundDrawable(AppCompatResources.getDrawable(this@MainActivity, R.drawable.ic_dropdown_background))
            }
            (textFields.codeName.editText as? MaterialAutoCompleteTextView)?.apply {
                setAdapter(codeNamesAdapter)
                setDropDownBackgroundDrawable(AppCompatResources.getDrawable(this@MainActivity, R.drawable.ic_dropdown_background))
            }
            (textFields.deviceRegion.editText as? MaterialAutoCompleteTextView)?.apply {
                setAdapter(deviceRegionAdapter)
                dropDownHeight = 280.dp
                setDropDownBackgroundDrawable(AppCompatResources.getDrawable(this@MainActivity, R.drawable.ic_dropdown_background))
            }
            (textFields.androidVersion.editText as? MaterialAutoCompleteTextView)?.apply {
                setAdapter(androidVersionAdapter)
                dropDownHeight = 280.dp
                setDropDownBackgroundDrawable(AppCompatResources.getDrawable(this@MainActivity, R.drawable.ic_dropdown_background))
            }

            // Setup TextChangedListener.
            codeNameWatcher = object : TextWatcher {
                override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                    textFields.deviceName.editText!!.removeTextChangedListener(deviceNameWatcher)
                    val text = try {
                        DeviceInfoHelper.deviceName(s.toString())
                    } catch (_: Exception) {
                        null
                    }
                    if (text != null) {
                        textFields.deviceName.editText!!.setText(text)
                    }
                }

                override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
                override fun afterTextChanged(s: Editable) {
                    textFields.deviceName.editText!!.addTextChangedListener(deviceNameWatcher)
                }
            }
            deviceNameWatcher = object : TextWatcher {
                override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                    textFields.codeName.editText!!.removeTextChangedListener(codeNameWatcher)
                    val text = try {
                        DeviceInfoHelper.codeName(s.toString())
                    } catch (_: Exception) {
                        null
                    }
                    if (text != null) {
                        textFields.codeName.editText!!.setText(text)
                    }
                }

                override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
                override fun afterTextChanged(s: Editable) {
                    textFields.codeName.editText!!.addTextChangedListener(codeNameWatcher)
                }
            }
            textFields.codeName.editText!!.addTextChangedListener(codeNameWatcher)
            textFields.deviceName.editText!!.addTextChangedListener(deviceNameWatcher)
        }
    }

    private fun setupCardViews() {
        mainContentBinding.apply {
            val firstViewArray = arrayOf(massageCard.romInfo, massageCard.firstInfo, massageCard.secondInfo)
            val secondViewArray = arrayOf(massageCard.secondInfo, massageCard.downloadInfo, massageCard.bigVersion, massageCard.bigVersionInfo)
            val firstViewArrayIncrement = arrayOf(incrementMassageCard.romInfo, incrementMassageCard.firstInfo, incrementMassageCard.secondInfo)
            val secondViewArrayIncrement = arrayOf(
                incrementMassageCard.secondInfo, incrementMassageCard.downloadInfo, incrementMassageCard.bigVersion, incrementMassageCard.bigVersionInfo
            )

            if (mainViewModel.device != null) {
                firstViewArray.forEach {
                    if (!it.isVisible) it.fadInAnimation()
                }
                activityMainBinding.implement.shrink()
                massageCard.romInfo.setTextAnimation(mainViewModel.type)
                massageCard.codenameInfo.setTextAnimation(mainViewModel.device)
                massageCard.systemInfo.setTextAnimation(mainViewModel.version)
                massageCard.codebaseInfo.setTextAnimation(mainViewModel.codebase)
                massageCard.branchInfo.setTextAnimation(mainViewModel.branch)
            } else {
                activityMainBinding.implement.extend()
                firstViewArray.forEach {
                    if (it.isVisible) it.fadOutAnimation()
                }
            }
            if (mainViewModel.filename != null) {
                secondViewArray.forEach {
                    if (!it.isVisible) it.fadInAnimation()
                }
                massageCard.bigVersionInfo.setTextAnimation(mainViewModel.bigversion)
                massageCard.filenameInfo.setTextAnimation(mainViewModel.filename)
                massageCard.filesizeInfo.setTextAnimation(mainViewModel.filesize)
                massageCard.changelogInfo.setTextAnimation(mainViewModel.changelog)
                massageCard.changelogInfo.setCopyClickListener(this@MainActivity, mainViewModel.changelog)
                massageCard.officialDownload.setDownloadClickListener(this@MainActivity, mainViewModel.filename, mainViewModel.officialDownload!!)
                massageCard.officialCopy.setCopyClickListener(this@MainActivity, mainViewModel.officialDownload)
                massageCard.cdn1Download.setDownloadClickListener(this@MainActivity, mainViewModel.filename, mainViewModel.cdn1Download!!)
                massageCard.cdn1Copy.setCopyClickListener(this@MainActivity, mainViewModel.cdn1Download)
                massageCard.cdn2Download.setDownloadClickListener(this@MainActivity, mainViewModel.filename, mainViewModel.cdn2Download!!)
                massageCard.cdn2Copy.setCopyClickListener(this@MainActivity, mainViewModel.cdn2Download)
            } else {
                secondViewArray.forEach {
                    if (it.isVisible) it.fadOutAnimation()
                }
            }
            if (mainViewModel.deviceIncrement != null) {
                firstViewArrayIncrement.forEach {
                    if (!it.isVisible) it.fadInAnimation()
                }
                incrementMassageCard.romInfo.setTextAnimation(mainViewModel.typeIncrement)
                incrementMassageCard.codenameInfo.setTextAnimation(mainViewModel.deviceIncrement)
                incrementMassageCard.systemInfo.setTextAnimation(mainViewModel.versionIncrement)
                incrementMassageCard.codebaseInfo.setTextAnimation(mainViewModel.codebaseIncrement)
                incrementMassageCard.branchInfo.setTextAnimation(mainViewModel.branchIncrement)
            } else {
                firstViewArrayIncrement.forEach {
                    if (it.isVisible) it.fadOutAnimation()
                }
            }
            if (mainViewModel.filenameIncrement != null) {
                secondViewArrayIncrement.forEach {
                    if (!it.isVisible) it.fadInAnimation()
                }
                incrementMassageCard.bigVersionInfo.setTextAnimation(mainViewModel.bigversionIncrement)
                incrementMassageCard.filenameInfo.setTextAnimation(mainViewModel.filenameIncrement)
                incrementMassageCard.filesizeInfo.setTextAnimation(mainViewModel.filesizeIncrement)
                incrementMassageCard.changelogInfo.setTextAnimation(mainViewModel.changelogIncrement)
                incrementMassageCard.changelogInfo.setCopyClickListener(this@MainActivity, mainViewModel.changelogIncrement)
                incrementMassageCard.officialDownload.setDownloadClickListener(
                    this@MainActivity, mainViewModel.filenameIncrement, mainViewModel.officialDownloadIncrement!!
                )
                incrementMassageCard.officialCopy.setCopyClickListener(this@MainActivity, mainViewModel.officialDownloadIncrement)
                incrementMassageCard.cdn1Download.setDownloadClickListener(
                    this@MainActivity, mainViewModel.filenameIncrement, mainViewModel.cdn1DownloadIncrement!!
                )
                incrementMassageCard.cdn1Copy.setCopyClickListener(this@MainActivity, mainViewModel.cdn1DownloadIncrement)
                incrementMassageCard.cdn2Download.setDownloadClickListener(
                    this@MainActivity, mainViewModel.filenameIncrement, mainViewModel.cdn2DownloadIncrement!!
                )
                incrementMassageCard.cdn2Copy.setCopyClickListener(this@MainActivity, mainViewModel.cdn2DownloadIncrement)
            } else {
                secondViewArrayIncrement.forEach {
                    if (it.isVisible) it.fadOutAnimation()
                }
            }
        }
    }

    private fun setupTopAppBar() {
        activityMainBinding.toolbar.apply {
            setNavigationOnClickListener {
                hapticConfirm(this)
                showAboutDialog()
            }
            setOnMenuItemClickListener { menuItem ->
                hapticConfirm(this)
                when (menuItem.itemId) {
                    R.id.login -> showLoginDialog()
                    R.id.logout -> showLogoutDialog()
                }
                false
            }
        }
    }

    private fun checkIfLoggedIn() {
        if (FileUtils.isCookiesFileExists(this@MainActivity)) {
            val cookiesFile = FileUtils.readCookiesFile(this@MainActivity)
            val cookies = json.decodeFromString<MutableMap<String, String>>(cookiesFile)
            val description = cookies["description"].toString()
            val authResult = cookies["authResult"].toString()
            if (authResult == "-1") {
                mainContentBinding.apply {
                    loginCard.loginIcon.setImageResource(R.drawable.ic_error)
                    loginCard.loginTitle.text = getString(R.string.login_expired)
                    loginCard.loginDesc.text = getString(R.string.login_expired_desc)
                }
            } else if (description.isNotEmpty()) {
                mainContentBinding.apply {
                    loginCard.loginIcon.setImageResource(R.drawable.ic_check_circle)
                    loginCard.loginTitle.text = getString(R.string.logged_in)
                    loginCard.loginDesc.text = getString(R.string.using_v2)
                }
                activityMainBinding.apply {
                    toolbar.menu.findItem(R.id.login).isVisible = false
                    toolbar.menu.findItem(R.id.logout).isVisible = true
                }
            }
        }
    }

    private fun createDialogView(): LinearLayout {
        return LinearLayout(this@MainActivity).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT)
            orientation = LinearLayout.VERTICAL
        }
    }

    private fun createTextInputLayout(hint: String, endIconMode: Int = TextInputLayout.END_ICON_NONE): TextInputLayout {
        return TextInputLayout(this@MainActivity).apply {
            this.hint = hint
            this.endIconMode = endIconMode
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(25.dp, 8.dp, 25.dp, 0.dp)
            }
        }
    }

    private fun createTextInputEditText(inputType: Int = InputType.TYPE_CLASS_TEXT): TextInputEditText {
        return TextInputEditText(this@MainActivity).apply {
            this.inputType = inputType
        }
    }

    private fun createCheckBox(prefKey: String, textId: Int, layoutParams: LinearLayout.LayoutParams): MaterialCheckBox {
        return MaterialCheckBox(this).apply {
            isChecked = prefs.getString(prefKey, "") == "1"
            minimumHeight = 0
            text = getString(textId)
            this.layoutParams = layoutParams
        }
    }
}
