package com.example

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {

    private lateinit var latexViewModel: LatexViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        setContent {
            MyApplicationTheme(darkTheme = false) { // Geometric Balance theme
                val viewModel: LatexViewModel = viewModel()
                latexViewModel = viewModel

                val context = LocalContext.current
                
                // On initial composition, if a Uri was shared via intent, load it; otherwise trigger auto-compile of template
                LaunchedEffect(Unit) {
                    val intentUri = intent?.data
                    if (intentUri != null) {
                        viewModel.loadFromUri(context, intentUri)
                    } else {
                        // Load default and pre-compile
                        viewModel.compileLatex(context)
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    LatexAppScreen(viewModel = viewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.data?.let { uri ->
            if (::latexViewModel.isInitialized) {
                latexViewModel.loadFromUri(this, uri)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LatexAppScreen(viewModel: LatexViewModel) {
    val context = LocalContext.current
    val currentUri by viewModel.currentUri.collectAsState()
    val fileName by viewModel.fileName.collectAsState()
    val latexContent by viewModel.latexContent.collectAsState()
    val isEditing by viewModel.isEditing.collectAsState()
    val compileState by viewModel.compileState.collectAsState()
    val pdfFile by viewModel.pdfFile.collectAsState()

    // SAF File selection launcher
    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            viewModel.loadFromUri(context, it)
        }
    }

    // SAF Save as document launcher
    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/x-tex")
    ) { uri ->
        uri?.let {
            viewModel.saveAsUri(context, it, 
                onSuccess = { Toast.makeText(context, "Saved successfully!", Toast.LENGTH_SHORT).show() },
                onFailure = { err -> Toast.makeText(context, err, Toast.LENGTH_LONG).show() }
            )
        }
    }

    // SAF Save compiled PDF launcher
    val exportPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        uri?.let {
            viewModel.exportPdfToUri(context, it,
                onSuccess = { Toast.makeText(context, "PDF saved successfully!", Toast.LENGTH_SHORT).show() },
                onFailure = { err -> Toast.makeText(context, err, Toast.LENGTH_LONG).show() }
            )
        }
    }

    var showSettingsDialog by remember { mutableStateOf(false) }
    var isMenuExpanded by remember { mutableStateOf(false) }

    if (showSettingsDialog) {
        val compileMode by viewModel.compileMode.collectAsState()
        
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = {
                Text(
                    "Compilation Mode",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = BrandText
                    )
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Configure how LaTeX source is compiled into PDFs.",
                        style = MaterialTheme.typography.bodyMedium.copy(color = BrandMutedText)
                    )
                    
                    // Mode Options
                    listOf(
                        Triple(CompileMode.AUTO, "Auto (Hybrid)", "Try remote online compiler first, fallback seamlessly to local Tectonic compiler."),
                        Triple(CompileMode.ONLINE, "Online", "Always compile remotely via high-fidelity Tectonic engine (requires Internet)."),
                        Triple(CompileMode.OFFLINE, "Offline", "Always compile on-device locally using the Tectonic Offline Engine. Zero bandwidth.")
                    ).forEach { (mode, name, desc) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = compileMode == mode,
                                    onClick = { viewModel.setCompileMode(mode) }
                                )
                                .padding(vertical = 8.dp)
                        ) {
                            RadioButton(
                                selected = compileMode == mode,
                                onClick = { viewModel.setCompileMode(mode) },
                                colors = RadioButtonDefaults.colors(selectedColor = BrandPurple)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = BrandText
                                    )
                                )
                                Text(
                                    text = desc,
                                    style = MaterialTheme.typography.bodySmall.copy(color = BrandMutedText)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showSettingsDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPurple)
                ) {
                    Text("Done")
                }
            },
            containerColor = Color.White
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BrandBackground,
                    titleContentColor = BrandText
                ),
                title = {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            LatexLogo()
                        }
                        Text(
                            text = fileName,
                            style = MaterialTheme.typography.bodySmall.copy(color = BrandMutedText),
                            maxLines = 1
                        )
                    }
                },
                actions = {
                    // Compile Button
                    IconButton(
                        onClick = {
                            viewModel.compileLatex(context)
                            // Auto transition to preview to see compilation status
                            viewModel.setEditing(false)
                        },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = BrandLavender
                        ),
                        modifier = Modifier.padding(horizontal = 4.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Compile LaTeX", tint = BrandDarkPurple)
                    }

                    // More Options Dropdown Menu Button
                    Box {
                        IconButton(onClick = { isMenuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More Options", tint = BrandMutedText)
                        }
                        DropdownMenu(
                            expanded = isMenuExpanded,
                            onDismissRequest = { isMenuExpanded = false },
                            modifier = Modifier.background(Color.White)
                        ) {
                            DropdownMenuItem(
                                text = { Text("New Document") },
                                leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, tint = BrandPurple) },
                                onClick = {
                                    isMenuExpanded = false
                                    viewModel.createNewDocument()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Open File") },
                                leadingIcon = { Icon(Icons.Default.FolderOpen, contentDescription = null, tint = BrandPurple) },
                                onClick = {
                                    isMenuExpanded = false
                                    openDocumentLauncher.launch(arrayOf("text/*", "application/octet-stream"))
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Save") },
                                leadingIcon = { Icon(Icons.Default.Save, contentDescription = null, tint = BrandPurple) },
                                onClick = {
                                    isMenuExpanded = false
                                    if (currentUri != null) {
                                        viewModel.saveEdits(context,
                                            onSuccess = { Toast.makeText(context, "Changes saved!", Toast.LENGTH_SHORT).show() },
                                            onFailure = { err -> Toast.makeText(context, err, Toast.LENGTH_LONG).show() }
                                        )
                                    } else {
                                        createDocumentLauncher.launch("document.tex")
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Save As...") },
                                leadingIcon = { Icon(Icons.Default.DriveFileMove, contentDescription = null, tint = BrandPurple) },
                                onClick = {
                                    isMenuExpanded = false
                                    createDocumentLauncher.launch(fileName)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Save PDF As...") },
                                leadingIcon = { Icon(Icons.Default.Save, contentDescription = "Save PDF", tint = BrandPurple) },
                                onClick = {
                                    isMenuExpanded = false
                                    val pdfFileVal = viewModel.pdfFile.value
                                    if (pdfFileVal != null && pdfFileVal.exists()) {
                                        val initialName = if (fileName.contains(".")) {
                                            fileName.substringBeforeLast(".") + ".pdf"
                                        } else {
                                            "$fileName.pdf"
                                        }
                                        exportPdfLauncher.launch(initialName)
                                    } else {
                                        Toast.makeText(context, "Please compile the document first to generate a PDF.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Settings") },
                                leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null, tint = BrandPurple) },
                                onClick = {
                                    isMenuExpanded = false
                                    showSettingsDialog = true
                                }
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (isEditing) {
                FloatingActionButton(
                    onClick = {
                        // Compile and toggle view
                        viewModel.compileLatex(context)
                        viewModel.setEditing(false)
                    },
                    containerColor = BrandLavender,
                    contentColor = BrandDarkPurple,
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Visibility,
                        contentDescription = "Preview PDF"
                    )
                }
            } else {
                FloatingActionButton(
                    onClick = {
                        viewModel.setEditing(true)
                    },
                    containerColor = BrandLavender,
                    contentColor = BrandDarkPurple,
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Source"
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(BrandBackground)
        ) {
            if (isEditing) {
                CodeEditorView(
                    content = latexContent,
                    onContentChange = { viewModel.updateContent(it) }
                )
            } else {
                val compileLog by viewModel.compileLog.collectAsState()
                PDFPreviewView(
                    compileState = compileState,
                    pdfFile = pdfFile,
                    compileLog = compileLog,
                    onRetryCompile = { viewModel.compileLatex(context) },
                    onSwitchToEdit = { viewModel.setEditing(true) },
                    onExportPdf = {
                        val pdfFileVal = viewModel.pdfFile.value
                        if (pdfFileVal != null && pdfFileVal.exists()) {
                            val initialName = if (fileName.contains(".")) {
                                fileName.substringBeforeLast(".") + ".pdf"
                            } else {
                                "$fileName.pdf"
                            }
                            exportPdfLauncher.launch(initialName)
                        } else {
                            Toast.makeText(context, "Please compile the document first to generate a PDF.", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun CodeEditorView(
    content: String,
    onContentChange: (String) -> Unit
) {
    var lineCount by remember(content) { mutableStateOf(content.split("\n").size) }
    val lineScrollState = rememberScrollState()
    val editorScrollState = rememberScrollState()

    // Sync scroll of line numbers column and editor text box
    LaunchedEffect(editorScrollState.value) {
        lineScrollState.scrollTo(editorScrollState.value)
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A)) // Editor background Slate 900
    ) {
        // Line Numbers Column
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(44.dp)
                .background(Color(0xFF020617)) // Darker gutter Slate 950
                .verticalScroll(lineScrollState)
                .padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.End
        ) {
            for (i in 1..lineCount) {
                Text(
                    text = "$i",
                    style = TextStyle(
                        color = Color(0xFF475569), // Muted blue grey
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.End
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(20.dp)
                        .padding(end = 4.dp)
                )
            }
        }

        // Divider
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(1.dp)
                .background(Color(0xFF334155))
        )

        // Custom Syntax Highlighted Monospace Field
        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
                .verticalScroll(editorScrollState)
                .padding(vertical = 12.dp, horizontal = 8.dp)
        ) {
            BasicTextField(
                value = content,
                onValueChange = onContentChange,
                textStyle = TextStyle(
                    color = Color(0xFFF8FAFC), // Off-white Slate 50
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 20.sp
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                visualTransformation = LatexSyntaxTransformation(
                    commandColor = Color(0xFF818CF8), // Indigo 400
                    bracketColor = Color(0xFFF59E0B), // Amber 500
                    commentColor = Color(0xFF64748B), // Slate 500
                    mathColor = Color(0xFF34D399) // Emerald 400
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
            )
        }
    }
}

@Composable
fun PDFPreviewView(
    compileState: CompileState,
    pdfFile: File?,
    compileLog: String,
    onRetryCompile: () -> Unit,
    onSwitchToEdit: () -> Unit,
    onExportPdf: () -> Unit
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    var pageCount by remember(pdfFile, compileState) { mutableStateOf(0) }
    var isLogExpanded by remember { mutableStateOf(false) }

    // Count total pages when compilation is successful
    LaunchedEffect(pdfFile, compileState) {
        if (compileState is CompileState.Success && pdfFile != null && pdfFile.exists()) {
            withContext(Dispatchers.IO) {
                try {
                    ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                        PdfRenderer(pfd).use { renderer ->
                            pageCount = renderer.pageCount
                        }
                    }
                } catch (e: Exception) {
                    Log.e("PDFPreviewView", "Error counting PDF pages", e)
                }
            }
        } else {
            pageCount = 0
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BrandBackground)
    ) {
        // Document Card Container - Upper space
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color(0xFF0D1117)),
            contentAlignment = Alignment.Center
        ) {
            // Document Contents
            when (compileState) {
                is CompileState.Idle -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Build,
                            contentDescription = null,
                            tint = BrandMutedText,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Ready to Compile",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = BrandText
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Tap the Compile button at the top to render.",
                            style = MaterialTheme.typography.bodyMedium.copy(color = BrandMutedText),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                is CompileState.Compiling -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        CircularProgressIndicator(
                            color = BrandPurple,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Compiling LaTeX Source...",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = BrandText,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Invoking remote compiler engine & rendering PDF",
                            style = MaterialTheme.typography.bodySmall.copy(color = BrandMutedText),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                is CompileState.Success -> {
                    if (pdfFile != null && pdfFile.exists() && pageCount > 0) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                items(pageCount) { index ->
                                    Box(
                                        modifier = Modifier.fillMaxWidth(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        PdfPageItem(
                                            pdfFile = pdfFile,
                                            pageIndex = index,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                    if (index < pageCount - 1) {
                                        Spacer(modifier = Modifier.height(12.dp))
                                    }
                                }
                            }

                            // High-fidelity Floating Page Count Badge in upper right corner matching the Geometric Balance mockup!
                            val firstVisibleIndex = remember { derivedStateOf { listState.firstVisibleItemIndex } }
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(12.dp)
                                    .background(Color(0xFFFEF7FF), RoundedCornerShape(8.dp))
                                    .border(1.dp, Color(0xFFD0BCFF), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "PAGE ${firstVisibleIndex.value + 1}/$pageCount",
                                    style = TextStyle(
                                        color = Color(0xFF6750A4),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(56.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "PDF Not Generated",
                                style = MaterialTheme.typography.titleMedium.copy(color = BrandText)
                            )
                        }
                    }
                }

                is CompileState.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "LaTeX Compile Failed",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = BrandText
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "See the error details in the log console below.",
                            style = MaterialTheme.typography.bodySmall.copy(color = BrandMutedText),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                        ) {
                            Button(
                                onClick = onSwitchToEdit,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = BrandPurple)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Edit Code", fontSize = 13.sp)
                            }
                            OutlinedButton(
                                onClick = onRetryCompile,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = BrandPurple),
                                border = BorderStroke(1.dp, BrandPurple)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Retry", fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // High-fidelity Compile Log Box at the bottom!
        // matching the exact visual layout from the HTML mockup:
        // bg-[#211f26] rounded-2xl p-4 flex flex-col shadow-inner relative
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                .height(if (isLogExpanded) 140.dp else 40.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(ConsoleBackground)
                .clickable { isLogExpanded = !isLogExpanded }
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header row with animated/pulsing indicator and chevron toggle
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val indicatorColor = when (compileState) {
                        is CompileState.Idle -> Color.Gray
                        is CompileState.Compiling -> Color(0xFFFFB74D) // Warm Orange
                        is CompileState.Success -> ConsoleSuccess
                        is CompileState.Error -> ConsoleError
                    }
                    
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(indicatorColor)
                    )
                    
                    Spacer(modifier = Modifier.width(6.dp))
                    
                    Text(
                        text = "COMPILE LOG",
                        style = TextStyle(
                            color = Color(0xFFE6E1E5),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    Icon(
                        imageVector = if (isLogExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                        contentDescription = if (isLogExpanded) "Collapse Log" else "Expand Log",
                        tint = Color(0xFFE6E1E5),
                        modifier = Modifier.size(18.dp)
                    )
                }

                if (isLogExpanded) {
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Scrollable Monospace Logs inside a SelectionContainer
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .clickable(enabled = false) { } // prevent clicks on log text from toggling expand state
                    ) {
                        SelectionContainer {
                            val textStyle = TextStyle(
                                color = when (compileState) {
                                    is CompileState.Error -> ConsoleError
                                    is CompileState.Success -> ConsoleSuccess
                                    else -> ConsoleText
                                },
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                lineHeight = 14.sp
                            )

                            Text(
                                text = compileLog,
                                style = textStyle
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PdfPageItem(
    pdfFile: File,
    pageIndex: Int,
    modifier: Modifier = Modifier
) {
    var bitmap by remember(pdfFile, pageIndex) { mutableStateOf<Bitmap?>(null) }
    var renderError by remember(pdfFile, pageIndex) { mutableStateOf(false) }

    LaunchedEffect(pdfFile, pageIndex) {
        withContext(Dispatchers.IO) {
            var pfd: ParcelFileDescriptor? = null
            var renderer: PdfRenderer? = null
            try {
                pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
                renderer = PdfRenderer(pfd)
                if (pageIndex < renderer.pageCount) {
                    val page = renderer.openPage(pageIndex)
                    try {
                        // Sharp resolution multiplier
                        val scale = 2.0f
                        val destWidth = (page.width * scale).toInt()
                        val destHeight = (page.height * scale).toInt()
                        
                        val bmp = Bitmap.createBitmap(destWidth, destHeight, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(android.graphics.Color.WHITE)
                        
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bitmap = bmp
                    } finally {
                        page.close()
                    }
                }
            } catch (e: Exception) {
                Log.e("PdfPageItem", "Failed to render page $pageIndex", e)
                renderError = true
            } finally {
                renderer?.close()
                pfd?.close()
            }
        }
    }

    Box(
        modifier = modifier
            .background(Color(0xFF0D1117))
            .aspectRatio(bitmap?.let { it.width.toFloat() / it.height } ?: 0.707f), // default is A4 aspect ratio
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = "Page ${pageIndex + 1}",
                modifier = Modifier.fillMaxSize()
            )
        } else if (renderError) {
            Text("Error rendering page ${pageIndex + 1}", color = MaterialTheme.colorScheme.error)
        } else {
            CircularProgressIndicator(color = BrandPurple)
        }
    }
}

// Help with highlighting selection container (requires material foundation selection)
@Composable
fun SelectionContainer(content: @Composable () -> Unit) {
    androidx.compose.foundation.text.selection.SelectionContainer {
        content()
    }
}

class LatexSyntaxTransformation(
    private val commandColor: Color,
    private val bracketColor: Color,
    private val commentColor: Color,
    private val mathColor: Color
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val builder = AnnotatedString.Builder()
        val rawText = text.text
        
        var i = 0
        while (i < rawText.length) {
            val char = rawText[i]
            
            if (char == '%') {
                // Comment line: % to end of line
                val lineEnd = rawText.indexOf('\n', i)
                val end = if (lineEnd != -1) lineEnd else rawText.length
                builder.withStyle(SpanStyle(color = commentColor)) {
                    append(rawText.substring(i, end))
                }
                i = end
            } else if (char == '\\') {
                // Command macro
                builder.withStyle(SpanStyle(color = commandColor)) {
                    append('\\')
                }
                i++
                var cmdStart = i
                while (i < rawText.length && rawText[i].isLetter()) {
                    i++
                }
                if (i > cmdStart) {
                    builder.withStyle(SpanStyle(color = commandColor)) {
                        append(rawText.substring(cmdStart, i))
                    }
                }
            } else if (char == '{' || char == '}' || char == '[' || char == ']') {
                // Formatting Brackets
                builder.withStyle(SpanStyle(color = bracketColor, fontWeight = FontWeight.Bold)) {
                    append(char)
                }
                i++
            } else if (char == '$') {
                // Math sign
                builder.withStyle(SpanStyle(color = mathColor)) {
                    append('$')
                }
                i++
            } else {
                builder.append(char)
                i++
            }
        }
        
        return TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
    }
}

@Composable
fun LatexLogo(modifier: Modifier = Modifier, color: Color = BrandPurple) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "L",
            style = MaterialTheme.typography.titleLarge.copy(
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.ExtraBold,
                color = color,
                fontSize = 20.sp
            )
        )
        Text(
            text = "A",
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.ExtraBold,
                color = color,
                fontSize = 13.sp
            ),
            modifier = Modifier.offset(x = (-1.5).dp, y = (-4).dp)
        )
        Text(
            text = "T",
            style = MaterialTheme.typography.titleLarge.copy(
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.ExtraBold,
                color = color,
                fontSize = 20.sp
            ),
            modifier = Modifier.offset(x = (-3).dp)
        )
        Text(
            text = "E",
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.ExtraBold,
                color = color,
                fontSize = 13.sp
            ),
            modifier = Modifier.offset(x = (-4.5).dp, y = 3.dp)
        )
        Text(
            text = "X",
            style = MaterialTheme.typography.titleLarge.copy(
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.ExtraBold,
                color = color,
                fontSize = 20.sp
            ),
            modifier = Modifier.offset(x = (-5.5).dp)
        )
    }
}
