@file:Suppress("DEPRECATION")

package com.example.transferprzezsiec

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.transferprzezsiec.ui.theme.TransferPrzezSiecTheme
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

var odstep = 4.dp

class MainActivity : ComponentActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var navController: NavHostController
    private lateinit var signInLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        val signInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            handleSignInResult(task.result, navController)
        }

        setContent {
            navController = rememberNavController()
            TransferPrzezSiecTheme {
                AppNavigation(navController = navController, signInLauncher = signInLauncher, googleSignInClient = googleSignInClient)
            }
        }
    }


    private fun handleSignInResult(account: GoogleSignInAccount?, navController: NavHostController) {
        account?.let {
            val credential = GoogleAuthProvider.getCredential(it.idToken, null)
            auth.signInWithCredential(credential)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        navController.navigate("signedIn")
                    } else {
                        // Sign in failed
                    }
                }
        }
    }
}


@Composable
fun LoginScreen(modifier: Modifier = Modifier, onGoogleSignInClick: () -> Unit, navController: NavHostController) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center
    ) {
        Button(
        onClick = onGoogleSignInClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Zaloguj się przez Google")
    }
    }

}

@Composable
fun SignedInScreen(navController: NavHostController) {
    val auth = FirebaseAuth.getInstance()
    val googleSignInClient = GoogleSignIn.getClient(LocalContext.current, GoogleSignInOptions.DEFAULT_SIGN_IN)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "Zalogowano")
            Spacer(modifier = Modifier.height(odstep))
            Row {
                Button(
                    onClick = { navController.navigate("Upload") },
                    modifier = Modifier.padding(end = odstep)
                ) {
                    Text("Zapisz")
                }
                Button(
                    onClick = { navController.navigate("Download") }
                ) {
                    Text("Odczytaj")
                }
            }
            Spacer(modifier = Modifier.height(odstep))
            Button(
                onClick = {
                    auth.signOut()
                    googleSignInClient.signOut().addOnCompleteListener {
                        navController.navigate("login") {
                            popUpTo("signedIn") { inclusive = true }
                        }
                    }
                }
            ) {
                Text("Wyloguj")
            }
        }
    }
}

@Composable
fun UploadScreen(navController: NavHostController) {
    val auth = FirebaseAuth.getInstance()
    val user = auth.currentUser
    val userName = user?.displayName ?: "Unknown"
    var text by remember { mutableStateOf("") }
    val context = LocalContext.current

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(userName)
            Spacer(modifier = Modifier.height(odstep))
            TextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            )
            Spacer(modifier = Modifier.height(odstep))
            Button(onClick = {
                val db = FirebaseFirestore.getInstance()
                val dateFormat = SimpleDateFormat("dd.MM.yyyy_HH:mm:ss", Locale.getDefault())
                val date = dateFormat.format(Date())
                val documentName = "document_$date"
                val document = hashMapOf(
                    "autor" to userName,
                    "tekst" to text,
                    "data" to date
                )
                db.collection("TransferPrzezSiec")
                    .document(documentName)
                    .set(document)
                    .addOnSuccessListener {
                        Toast.makeText(context, "Pomyślnie zapisano plik", Toast.LENGTH_SHORT).show()
                        text = ""
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(context, "Wystąpił błąd: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }) {
                Text("Zapisz")
            }
            Spacer(modifier = Modifier.height(odstep))
            Button(onClick = { navController.popBackStack() }) {
                Text("Wróć")
            }
        }
    }
}

@Composable
fun DownloadScreen(navController: NavHostController) {
    val context = LocalContext.current
    val savedStateHandle = navController.currentBackStackEntry?.savedStateHandle
    var author by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }

    savedStateHandle?.getLiveData<Map<String, Any>>("selectedDocument")?.observe(LocalLifecycleOwner.current) { document ->
        author = document["autor"] as? String ?: "No author"
        date = document["data"] as? String ?: "No date"
        text = document["tekst"] as? String ?: "No text"
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(author)
            Spacer(modifier = Modifier.height(odstep))
            Text(date)
            Spacer(modifier = Modifier.height(odstep))
            Text(text)
            Spacer(modifier = Modifier.height(odstep))
            Button(onClick = {
                navController.navigate("chooseFile")
            }) {
                Text("Wybierz plik")
            }
            Spacer(modifier = Modifier.height(odstep))
            Button(onClick = { navController.popBackStack() }) {
                Text("Wróć")
            }
        }
    }
}

@Composable
fun ChooseFileScreen(navController: NavHostController) {
    val db = FirebaseFirestore.getInstance()
    val documents = remember { mutableStateListOf<Map<String, Any>>() }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        db.collection("TransferPrzezSiec")
            .get()
            .addOnSuccessListener { result ->
                for (document in result) {
                    documents.add(document.data)
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "Wystąpił błąd: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            documents.forEach { document ->
                val documentName = document["data"] as? String ?: "Unknown"
                Text(documentName, modifier = Modifier.clickable {
                    Log.d("ChooseFileScreen", "Selected document: $document")
                    navController.previousBackStackEntry?.savedStateHandle?.set("selectedDocument", document)
                    navController.popBackStack()
                })
                Spacer(modifier = Modifier.height(odstep))
            }
            Button(onClick = { navController.popBackStack() }) {
                Text("Wróć")
            }
        }
    }
}

@Composable
fun AppNavigation(navController: NavHostController, signInLauncher: ActivityResultLauncher<Intent>, googleSignInClient: GoogleSignInClient) {
    NavHost(navController, startDestination = "login") {
        composable("login") {
            LoginScreen(
                onGoogleSignInClick = {
                    val signInIntent = googleSignInClient.signInIntent
                    signInLauncher.launch(signInIntent)
                },
                navController = navController
            )
        }
        composable("signedIn") { SignedInScreen(navController = navController) }
        composable("Upload") { UploadScreen(navController = navController) }
        composable("Download") { DownloadScreen(navController = navController) }
        composable("chooseFile") { ChooseFileScreen(navController = navController) }
    }
}