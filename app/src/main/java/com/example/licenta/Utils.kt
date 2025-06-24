package com.example.licenta.utils

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase


fun refreshAllUserStats(onDone: () -> Unit = {}) {
    val db = Firebase.firestore

    db.collection("trips").get().addOnSuccessListener { snap ->
        /* uid → (nrCurse, scorTotal) */
        val sums = mutableMapOf<String, Pair<Long, Double>>()

        snap.documents.forEach { doc ->
            /* acceptă UID atât din noul câmp (“uid”) cât și din vechiul (“userId”) */
            val uid   = doc.getString("uid") ?: doc.getString("userId") ?: return@forEach
            /* ignoră cursele marcate explicit finished = false */
            if (doc.getBoolean("finished") == false) return@forEach
            val score = doc.getDouble("score") ?: 0.0

            val (c, s) = sums[uid] ?: (0L to 0.0)
            sums[uid] = (c + 1) to (s + score)
        }

        db.collection("users").get().addOnSuccessListener { usersSnap ->
            val batch = db.batch()
            usersSnap.documents.forEach { u ->
                val uid = u.id
                val (cnt, tot) = sums[uid] ?: (0L to 0.0)
                batch.set(
                    db.collection("user-stats").document(uid),
                    mapOf("tripCount" to cnt, "totalScore" to tot)
                )
            }
            batch.commit().addOnSuccessListener { onDone() }
        }
    }
}

fun refreshUserStatsFromTrips(
    userId: String,
    onFinish: () -> Unit = {}
) {
    val db = Firebase.firestore

    db.collection("trips")
        .whereEqualTo("uid", userId)          // ❶ căutăm varianta NOUĂ
        .get()
        .addOnSuccessListener { newSnap ->

            /* Funcție locală: calculează & salvează în  user-stats/<uid> */
            fun computeAndSave(docs: List<DocumentSnapshot>) {
                val valid = docs.filter { it.getBoolean("finished") != false }
                val tripCount  = valid.size
                val totalScore = valid.mapNotNull { it.getDouble("score") }.sum()

                db.collection("user-stats")
                    .document(userId)
                    .set(mapOf("tripCount" to tripCount, "totalScore" to totalScore))
                    .addOnSuccessListener { onFinish() }
                    .addOnFailureListener { onFinish() }
            }

            db.collection("trips")
                .whereEqualTo("userId", userId)    // legacy field
                .get()
                .addOnSuccessListener { legacySnap ->
                    val combinedDocs = newSnap.documents + legacySnap.documents
                    computeAndSave(combinedDocs)
                }
                .addOnFailureListener {
                    /* dacă legacy-query eșuează, măcar salvăm ce am găsit */
                    computeAndSave(newSnap.documents)
                }
        }
        .addOnFailureListener { onFinish() }        // nu blocăm fluxul la eroare
}
