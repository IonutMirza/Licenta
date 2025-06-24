package com.example.licenta

import android.Manifest
import android.app.DatePickerDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.TimePickerDialog
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.work.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.licenta.model.Car
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import androidx.activity.result.contract.ActivityResultContracts
import androidx.work.OneTimeWorkRequestBuilder

// ExpiryCheckWorker.kt
class ExpiryCheckWorker(
    ctx: Context,
    params: WorkerParameters
) : CoroutineWorker(ctx, params) {

    private val firestore = FirebaseFirestore.getInstance()
    private val prefs = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val userId = FirebaseAuth.getInstance().currentUser?.uid

    override suspend fun doWork(): Result {
        if (userId == null) return Result.success()

        val now = Calendar.getInstance()
        val sdf = SimpleDateFormat("dd.MM.yyyy", Locale.US)

        val snapshot = firestore.collection("cars1")
            .whereEqualTo("userId", userId).get().await()

        snapshot.documents.forEach { doc ->
            val car = doc.toObject(Car::class.java) ?: return@forEach

            fun check(label: String, dateStr: String?, tag: String) {
                if (dateStr.isNullOrBlank()) return
                val date = sdf.parse(dateStr) ?: return
                val diff = (date.time - now.timeInMillis) / (1000 * 60 * 60 * 24)

                if (diff in 0..6) {
                    val notifId = (doc.id + tag).hashCode()

                    sendNotification(
                        "${car.brand} ${car.model}: $label expiră în $diff zile",
                        notifId
                    )
                }
            }


            check("Rovinieta",       car.rovinietaDate, "ROV")
            check("Asigurarea RCA",  car.insuranceDate, "RCA")
            check("ITP-ul",          car.itpDate,       "ITP")
        }

        return Result.success()
    }

    private fun sendNotification(text: String, id: Int) {
        val nm = NotificationManagerCompat.from(applicationContext)
        if (!nm.areNotificationsEnabled()) return

        val chanId = "expiry_channel"
        if (Build.VERSION.SDK_INT >= 26 &&
            nm.getNotificationChannel(chanId) == null
        ) {
            nm.createNotificationChannel(
                NotificationChannel(chanId, "Expirări documente",
                    NotificationManager.IMPORTANCE_DEFAULT)
            )
        }

        val notif = NotificationCompat.Builder(applicationContext, chanId)
            .setSmallIcon(R.drawable.expire)
            .setContentTitle("Atenție!!!")
            .setContentText(text)
            .setAutoCancel(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
                nm.notify(id, notif)
            } else {
                // Aici poți ignora notificarea sau cere permisiunea în UI
                // (doar Activitățile pot cere permisiuni, nu WorkManager!)
                Log.w("Notif", "Permisiunea POST_NOTIFICATIONS nu e acordată.")
            }
        } else {
            nm.notify(id, notif)
        }

    }
}


@Composable
fun GaragePage(
    onNavigateToMapPage: () -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToSettingsPage: () -> Unit,
    onCarSelected: (Car) -> Unit
) {
    val context = LocalContext.current
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(context, "Notificările sunt dezactivate!", Toast.LENGTH_SHORT).show()
        }
    }
    val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    val darkModeEnabled = remember { mutableStateOf(prefs.getBoolean("dark_mode", false)) }
    val isDark = darkModeEnabled.value
    val backgroundColor = if (isDark) Color(0xFF1E1E1E) else Color.White
    val textColor = if (isDark) Color.White else Color.Black

    val firestore = FirebaseFirestore.getInstance()
    val user = FirebaseAuth.getInstance().currentUser
    val userId = user?.uid ?: ""

    var selectedCar by remember { mutableStateOf<Car?>(null) }
    var cars by remember { mutableStateOf<List<Car>>(emptyList()) }
    var showEditDialog by remember { mutableStateOf(false) }
    var carToEdit by remember { mutableStateOf<Car?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var carToDelete by remember { mutableStateOf<Car?>(null) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var carToView by remember { mutableStateOf<Car?>(null) }
    var showNotesDialog by remember { mutableStateOf(false) }

    var rovinietaDate by remember { mutableStateOf("") }
    var insuranceDate by remember { mutableStateOf("") }
    var itpDate by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    // variabilă state
    var reminderTime by remember {
        mutableStateOf(prefs.getString("reminder_time", "09:00")!!)
    }

    fun scheduleExpiryWorker(ctx: Context) {
        val prefs = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val time    = prefs.getString("reminder_time", "09:00") ?: "09:00"
        val (h, m)  = time.split(":").map { it.toInt() }

        // calculăm offsetul până la următoarea execuție
        val calNow  = Calendar.getInstance()
        val calNext = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, h)
            set(Calendar.MINUTE, m)
            set(Calendar.SECOND, 0)
            if (before(calNow)) {
                // ora selectată a trecut – pornim worker-ul imediat,
                // apoi îl programăm pentru ziua următoare la aceeași oră
                WorkManager.getInstance(ctx).enqueue(
                    OneTimeWorkRequestBuilder<ExpiryCheckWorker>()
                        .setInitialDelay(1, TimeUnit.MINUTES)  // rulează peste ~1 minut
                        .build()
                )
                add(Calendar.DAY_OF_YEAR, 1)
            }

        }
        val initialDelay = calNext.timeInMillis - calNow.timeInMillis

        val work = PeriodicWorkRequestBuilder<ExpiryCheckWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)  // citește Firestore
                    .build()
            )
            .build()

        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
            "expiries_check",
            ExistingPeriodicWorkPolicy.UPDATE,
            work
        )
    }


    fun openDatePicker(initial: String, onDateSelected: (String) -> Unit) {
        val calendar = Calendar.getInstance()
        val dateParts = initial.split(".").mapNotNull { it.toIntOrNull() }
        if (dateParts.size == 3) {
            calendar.set(Calendar.DAY_OF_MONTH, dateParts[0])
            calendar.set(Calendar.MONTH, dateParts[1] - 1)
            calendar.set(Calendar.YEAR, dateParts[2])
        }
        DatePickerDialog(
            context,
            { _, year, month, day ->
                val date = "%02d.%02d.%04d".format(day, month + 1, year)
                onDateSelected(date)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    DisposableEffect(userId) {
        val listener = firestore.collection("cars1")
            .whereEqualTo("userId", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Toast.makeText(context, "Eroare la încărcarea mașinilor: ${error.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }
                val loaded = snapshot?.documents?.mapNotNull { it.toObject(Car::class.java) } ?: emptyList()
                cars = loaded
                if (selectedCar == null && cars.isNotEmpty()) {
                    selectedCar = cars.first()
                    onCarSelected(cars.first())
                }
            }
        onDispose { listener.remove() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .padding(24.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("My Garage", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = textColor, modifier = Modifier.padding(top = 16.dp))
        Spacer(modifier = Modifier.height(24.dp))

        selectedCar?.let {
            Text("Selected Car: ${it.brand} ${it.model}", fontSize = 18.sp, color = textColor)
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(onClick = {
            // Android 13+ → cerem permisiunea dacă nu există
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return@Button
            }

            TimePickerDialog(
                context,
                { _, h, m ->
                    reminderTime = "%02d:%02d".format(h, m)
                    prefs.edit().putString("reminder_time", reminderTime).apply()
                    scheduleExpiryWorker(context)            // pornește / actualizează job-ul
                    Toast.makeText(context, "Reminder set la $reminderTime", Toast.LENGTH_SHORT).show()
                },
                reminderTime.substring(0, 2).toInt(),
                reminderTime.substring(3, 5).toInt(),
                true
            ).show()
        }) {
            Text("Setează oră notificare: $reminderTime", color = textColor)
        }


        Spacer(modifier = Modifier.height(24.dp))

        if (cars.isEmpty()) {
            Text("No cars added yet.", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = textColor)
        } else {
            cars.sortedBy { it.brand }.forEach { car ->
                val isSelected = (selectedCar?.id == car.id)
                val itemBackground = if (isSelected) Color(0xFFB2DFDB) else if (isDark) Color(0xFF333333) else Color(0xFFE0E0E0)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                        .background(itemBackground, shape = RoundedCornerShape(8.dp))
                        .clickable {
                            selectedCar = car
                            onCarSelected(car)
                        }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${car.brand} ${car.model}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = textColor)
                        Text("Year: ${car.year}", fontSize = 16.sp, color = textColor)
                        Text("Plate: ${car.licensePlate}", fontSize = 16.sp, color = textColor)
                    }
                    IconButton(onClick = {
                        carToView = car
                        rovinietaDate = car.rovinietaDate.orEmpty()
                        insuranceDate = car.insuranceDate.orEmpty()
                        itpDate = car.itpDate.orEmpty()
                        notes = car.notes.orEmpty()
                        showInfoDialog = true
                    }) {
                        Icon(painter = painterResource(id = R.drawable.info), contentDescription = "Info", tint = Color.Gray)
                    }
                    IconButton(onClick = {
                        carToEdit = car
                        showEditDialog = true
                    }) {
                        Icon(painter = painterResource(id = R.drawable.edit), contentDescription = "Edit", tint = Color.Blue)
                    }
                    IconButton(onClick = {
                        carToDelete = car
                        showDeleteDialog = true
                    }) {
                        Icon(painter = painterResource(id = R.drawable.delete), contentDescription = "Delete", tint = Color.Red)
                    }
                }
            }
        }
    }

    if (showEditDialog && carToEdit != null) {
        var editedBrand by remember { mutableStateOf(carToEdit!!.brand) }
        var editedModel by remember { mutableStateOf(carToEdit!!.model) }
        var editedYear by remember { mutableStateOf(carToEdit!!.year.toString()) }

        AlertDialog(
            onDismissRequest = {
                showEditDialog = false
                carToEdit = null
            },
            title = { Text("Edit Car", color = textColor) },
            text = {
                Column {
                    OutlinedTextField(value = editedBrand, onValueChange = { editedBrand = it }, label = { Text("Brand", color = textColor) }, singleLine = true)
                    OutlinedTextField(value = editedModel, onValueChange = { editedModel = it }, label = { Text("Model", color = textColor) }, singleLine = true)
                    OutlinedTextField(value = editedYear, onValueChange = { editedYear = it }, label = { Text("Year", color = textColor) }, singleLine = true, keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number))
                }
            },
            confirmButton = {
                Button(onClick = {
                    val yearInt = editedYear.toIntOrNull()
                    if (editedBrand.isBlank() || editedModel.isBlank() || yearInt == null) {
                        Toast.makeText(context, "Completează toate câmpurile corect", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    val updatedCar = carToEdit!!.copy(brand = editedBrand.trim(), model = editedModel.trim(), year = yearInt)
                    firestore.collection("cars1")
                        .document(updatedCar.id)
                        .set(updatedCar)
                        .addOnSuccessListener {
                            cars = cars.map { if (it.id == updatedCar.id) updatedCar else it }
                            if (selectedCar?.id == updatedCar.id) {
                                selectedCar = updatedCar
                                onCarSelected(updatedCar)
                            }
                            Toast.makeText(context, "Mașină actualizată cu succes!", Toast.LENGTH_SHORT).show()
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(context, "Eroare la actualizare: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    showEditDialog = false
                    carToEdit = null
                }) {
                    Text("Salvează", color = textColor)
                }
            },
            dismissButton = {
                Button(onClick = {
                    showEditDialog = false
                    carToEdit = null
                }) {
                    Text("Anulează", color = textColor)
                }
            },
            containerColor = backgroundColor
        )
    }

    if (showDeleteDialog && carToDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Confirmare ștergere", color = textColor) },
            text = { Text("Ești sigur că vrei să ștergi această mașină?", color = textColor) },
            confirmButton = {
                Button(onClick = {
                    firestore.collection("cars1")
                        .document(carToDelete!!.id)
                        .delete()
                        .addOnSuccessListener {
                            cars = cars.filter { it.id != carToDelete!!.id }
                            if (selectedCar?.id == carToDelete!!.id) {
                                selectedCar = cars.firstOrNull()
                                selectedCar?.let { onCarSelected(it) }
                            }
                            Toast.makeText(context, "Mașină ștearsă cu succes.", Toast.LENGTH_SHORT).show()
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(context, "Eroare la ștergere: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    showDeleteDialog = false
                    carToDelete = null
                }) {
                    Text("Șterge", color = textColor)
                }
            },
            dismissButton = {
                Button(onClick = {
                    showDeleteDialog = false
                    carToDelete = null
                }) {
                    Text("Anulează", color = textColor)
                }
            },
            containerColor = backgroundColor
        )
    }

    if (showInfoDialog && carToView != null) {
        AlertDialog(
            onDismissRequest = {
                showInfoDialog = false
                carToView = null
            },
            title = { Text("Detalii Mașină", color = textColor) },
            text = {
                Column {
                    Text("Adaugă datele de expirare:", color = textColor)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { openDatePicker(rovinietaDate) { rovinietaDate = it } }) {
                        Text("Rovinietă: ${if (rovinietaDate.isNotBlank()) rovinietaDate else "Alege data"}", color = textColor)
                    }
                    Button(onClick = { openDatePicker(insuranceDate) { insuranceDate = it } }) {
                        Text("Asigurare RCA: ${if (insuranceDate.isNotBlank()) insuranceDate else "Alege data"}", color = textColor)
                    }
                    Button(onClick = { openDatePicker(itpDate) { itpDate = it } }) {
                        Text("ITP: ${if (itpDate.isNotBlank()) itpDate else "Alege data"}", color = textColor)
                    }
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { showNotesDialog = true }) {
                        Text("Deschide notițele", color = textColor)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val updatedCar = carToView!!.copy(
                        rovinietaDate = rovinietaDate,
                        insuranceDate = insuranceDate,
                        itpDate = itpDate,
                        notes = notes
                    )
                    firestore.collection("cars1")
                        .document(updatedCar.id)
                        .set(updatedCar)
                        .addOnSuccessListener {
                            cars = cars.map { if (it.id == updatedCar.id) updatedCar else it }
                            Toast.makeText(context, "Informații salvate!", Toast.LENGTH_SHORT).show()
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(context, "Eroare la salvare: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    showInfoDialog = false
                    carToView = null
                }) {
                    Text("Salvează", color = textColor)
                }
            },
            dismissButton = {
                Button(onClick = {
                    showInfoDialog = false
                    carToView = null
                }) {
                    Text("Închide", color = textColor)
                }
            },
            containerColor = backgroundColor
        )
    }

    if (showNotesDialog) {
        AlertDialog(
            onDismissRequest = { showNotesDialog = false },
            title = { Text("Notițe detaliate", color = textColor) },
            text = {
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Scrie aici", color = textColor) },
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    maxLines = 20
                )
            },
            confirmButton = {
                Button(onClick = { showNotesDialog = false }) {
                    Text("Gata", color = textColor)
                }
            },
            containerColor = backgroundColor
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Bottom,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            IconButton(onClick = { onNavigateBack() }, modifier = Modifier.size(50.dp)) {
                Icon(painter = painterResource(id = R.drawable.home), contentDescription = "Home", tint = Color.LightGray, modifier = Modifier.fillMaxSize())
            }
            IconButton(onClick = { onNavigateToMapPage() }, modifier = Modifier.size(50.dp)) {
                Icon(painter = painterResource(id = R.drawable.map), contentDescription = "Map", tint = Color.LightGray, modifier = Modifier.fillMaxSize())
            }
            IconButton(onClick = { }, modifier = Modifier.size(50.dp)) {
                Icon(painter = painterResource(id = R.drawable.garage), contentDescription = "Garage", tint = Color.Black, modifier = Modifier.fillMaxSize())
            }
            IconButton(onClick = { onNavigateToSettingsPage() }, modifier = Modifier.size(50.dp)) {
                Icon(painter = painterResource(id = R.drawable.settings), contentDescription = "Settings", tint = Color.LightGray, modifier = Modifier.fillMaxSize())
            }
        }
    }
}
