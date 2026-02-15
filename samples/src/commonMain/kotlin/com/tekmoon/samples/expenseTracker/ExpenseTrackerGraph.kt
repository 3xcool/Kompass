package com.tekmoon.kompass.samples.expenseTracker

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tekmoon.kompass.*
import com.tekmoon.kompass.util.BackPressedChannel
import kompasskmp.samples.generated.resources.Res
import kompasskmp.samples.generated.resources.ic_arrow_back
import kompasskmp.samples.generated.resources.ic_edit
import kotlinx.collections.immutable.persistentListOf
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.painterResource

/**
 * Format a Double as currency without using String.format()
 * Handles 2 decimal places properly
 */
fun Double.formatCurrency(): String {
    val cents = (this * 100).toLong()
    val dollars = cents / 100
    val remainingCents = cents % 100
    return "$$dollars.${remainingCents.toString().padStart(2, '0')}"
}


@Serializable
data class Client(
    val id: String,
    val name: String,
    val totalExpenses: Double,
    val expenseCount: Int
)

@Serializable
data class Expense(
    val id: String,
    val title: String,
    val amount: Double,
    val date: String,
    val category: String
)


private enum class ExpenseTrackerDest : Destination {
    ClientsList,
    ClientDetail,
    ExpenseDetail;

    override val id: String get() = "kompass/expensetracker/$name"
}

@Serializable
private data class ClientDetailArgs(val clientId: String)

@Serializable
private data class ExpenseDetailArgs(val expenseId: String, val clientId: String)

/* -------------------------------------------
 * Mock Data
 * ------------------------------------------- */

object MockData {
    val clients = persistentListOf(
        Client("1", "Acme Corp", 15_234.50, 24),
        Client("2", "TechStart Inc", 8_945.75, 15),
        Client("3", "Global Solutions", 32_456.00, 42),
        Client("4", "Innovation Labs", 5_678.25, 9),
        Client("5", "CloudSync Systems", 28_567.89, 38),
        Client("6", "DataFlow Analytics", 19_234.56, 29),
        Client("7", "NexGen Technologies", 41_890.12, 56),
        Client("8", "Quantum Enterprises", 12_345.67, 18)
    )

    fun getExpensesForClient(clientId: String): List<Expense> {
        return when (clientId) {
            "1" -> listOf(
                Expense("e1", "Office Supplies", 245.50, "2024-02-10", "Supplies"),
                Expense("e2", "Software License", 1_299.00, "2024-02-09", "Software"),
                Expense("e3", "Team Lunch", 156.75, "2024-02-08", "Meals"),
                Expense("e4", "Internet Bill", 89.99, "2024-02-05", "Utilities"),
                Expense("e5", "Marketing Campaign", 2_500.00, "2024-02-04", "Marketing"),
                Expense("e6", "Hardware Equipment", 3_899.99, "2024-02-01", "Equipment")
            )
            "2" -> listOf(
                Expense("e7", "Cloud Services", 450.00, "2024-02-10", "IT"),
                Expense("e8", "Marketing Materials", 567.50, "2024-02-09", "Marketing"),
                Expense("e9", "Travel Booking", 1_234.56, "2024-02-07", "Travel"),
                Expense("e10", "Conference Registration", 999.00, "2024-02-05", "Events")
            )
            "3" -> listOf(
                Expense("e11", "Conference Registration", 2_500.00, "2024-02-10", "Events"),
                Expense("e12", "Travel Accommodation", 3_200.00, "2024-02-08", "Travel"),
                Expense("e13", "Team Building Event", 1_800.00, "2024-02-06", "Events"),
                Expense("e14", "Software Licenses", 5_600.00, "2024-02-03", "Software"),
                Expense("e15", "Office Furniture", 4_250.00, "2024-02-01", "Equipment"),
                Expense("e16", "Catering Services", 890.50, "2024-01-30", "Meals")
            )
            "4" -> listOf(
                Expense("e17", "Office Equipment", 899.99, "2024-02-10", "Equipment"),
                Expense("e18", "Network Setup", 2_100.00, "2024-02-08", "IT"),
                Expense("e19", "Furniture Upgrade", 1_450.00, "2024-02-05", "Equipment")
            )
            "5" -> listOf(
                Expense("e20", "AWS Services", 5_200.00, "2024-02-10", "IT"),
                Expense("e21", "Database Licensing", 3_400.00, "2024-02-09", "Software"),
                Expense("e22", "Security Audit", 2_800.00, "2024-02-07", "IT"),
                Expense("e23", "Developer Tools", 1_200.00, "2024-02-05", "Software"),
                Expense("e24", "Infrastructure Setup", 8_900.00, "2024-02-01", "IT")
            )
            "6" -> listOf(
                Expense("e25", "Analytics Platform", 6_500.00, "2024-02-10", "Software"),
                Expense("e26", "Data Storage", 2_300.00, "2024-02-09", "IT"),
                Expense("e27", "Training Workshop", 1_800.00, "2024-02-07", "Training"),
                Expense("e28", "Consulting Services", 4_200.00, "2024-02-05", "Consulting")
            )
            "7" -> listOf(
                Expense("e29", "Enterprise Solutions", 12_500.00, "2024-02-10", "Software"),
                Expense("e30", "Implementation Services", 8_900.00, "2024-02-09", "Consulting"),
                Expense("e31", "Infrastructure", 15_000.00, "2024-02-08", "IT"),
                Expense("e32", "Support Contract", 5_600.00, "2024-02-05", "Support"),
                Expense("e33", "Training Programs", 3_400.00, "2024-02-02", "Training")
            )
            else -> listOf(
                Expense("e34", "Office Equipment", 899.99, "2024-02-10", "Equipment"),
                Expense("e35", "Software Subscription", 299.00, "2024-02-09", "Software"),
                Expense("e36", "Internet Service", 75.00, "2024-02-05", "Utilities")
            )
        }
    }
}

/* -------------------------------------------
 * Navigation Graph
 * ------------------------------------------- */

object ExpenseTrackerGraph : NavigationGraph {

    override val sceneLayout = SceneLayoutListDetail(
        compactWidthThreshold = 700.dp,
        transition = ExpenseTrackerFastTransition
    )

    override fun canResolveDestination(destinationId: String): Boolean =
        ExpenseTrackerDest.entries.any { it.id == destinationId }

    override fun resolveDestination(
        destinationId: String,
        args: String?
    ): Destination =
        ExpenseTrackerDest.entries.first { it.id == destinationId }

    @Composable
    override fun Content(
        entry: BackStackEntry,
        destination: Destination,
        navController: NavController
    ) {
        when (destination) {
            ExpenseTrackerDest.ClientsList ->
                ClientsListScreen(navController)

            ExpenseTrackerDest.ClientDetail ->
                ClientDetailScreen(entry, navController)

            ExpenseTrackerDest.ExpenseDetail ->
                ExpenseDetailScreen(entry, navController)
        }
    }
}

/* -------------------------------------------
 * Root Composable
 * ------------------------------------------- */

@Composable
fun ExpenseTrackerApp(
    backPressedChannel: BackPressedChannel?,
    onDismiss: () -> Unit = {}
) {
    val navController = rememberNavController(ExpenseTrackerDest.ClientsList)

    PlatformBackHandler(backPressedChannel = backPressedChannel) {
        navController.popIfCan { onDismiss() }
    }

    KompassNavigationHost(
        navController = navController,
        graphs = persistentListOf(ExpenseTrackerGraph)
    )
}

/* -------------------------------------------
 * Screens
 * ------------------------------------------- */

@Composable
private fun ClientsListScreen(navController: NavController) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Header - Clients List (Blue)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1E40AF))  // Deep Blue
                .padding(24.dp)
        ) {
            Text(
                "Clients",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                "Manage all clients",
                fontSize = 14.sp,
                color = Color(0xFFBFDBFE),
                modifier = Modifier.paddingFromBaseline(top = 8.dp)
            )
        }

        // Clients List
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(MockData.clients) { client ->
                ClientCard(
                    client = client,
                    onClick = {
                        navController.navigate(
                            entry = ExpenseTrackerDest.ClientDetail.toBackStackEntry(
                                args = Json.encodeToString(
                                    ClientDetailArgs(client.id)
                                ),
                                scopeId = newScope()
                            )
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun ClientCard(
    client: Client,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    client.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "${client.expenseCount} expenses",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }

            Column(
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    client.totalExpenses.formatCurrency(),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClientDetailScreen(
    entry: BackStackEntry,
    navController: NavController
) {
    val args = entry.args?.let {
        Json.decodeFromString<ClientDetailArgs>(it)
    }

    val client = remember {
        MockData.clients.find { it.id == args?.clientId }
            ?: MockData.clients.first()
    }

    val expenses = remember { MockData.getExpensesForClient(client.id) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Header with back button
        TopAppBar(
            title = { Text(client.name) },
            navigationIcon = {
                IconButton(onClick = { navController.pop() }) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_arrow_back),
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color(0xFF7C3AED),  // Purple
                titleContentColor = Color.White,
                navigationIconContentColor = Color.White
            )
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Summary Card
            item {
                SummaryCard(client)
            }

            // Expenses Section
            item {
                Text(
                    "Recent Expenses",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF374151),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            items(expenses) { expense ->
                ExpenseListItem(
                    expense = expense,
                    onClick = {
                        navController.navigate(
                            entry = ExpenseTrackerDest.ExpenseDetail.toBackStackEntry(
                                args = Json.encodeToString(
                                    ExpenseDetailArgs(expense.id, client.id)
                                ),
                                scopeId = newScope()
                            )
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun SummaryCard(client: Client) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Total Expenses",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Text(
                client.totalExpenses.formatCurrency(),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatItem("Transactions", client.expenseCount.toString())
                StatItem("Status", "Active")
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column {
        Text(
            label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Text(
            value,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ExpenseListItem(
    expense: Expense,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    expense.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    expense.date,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    expense.amount.formatCurrency(),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.secondary
                )
                Text(
                    expense.category,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpenseDetailScreen(
    entry: BackStackEntry,
    navController: NavController
) {
    val args = entry.args?.let {
        Json.decodeFromString<ExpenseDetailArgs>(it)
    }

    val client = remember {
        MockData.clients.find { it.id == args?.clientId }
            ?: MockData.clients.first()
    }

    val expense = remember {
        MockData.getExpensesForClient(client.id).find { it.id == args?.expenseId }
            ?: Expense("", "Unknown", 0.0, "", "")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TopAppBar(
            title = { Text("Expense Details") },
            navigationIcon = {
                IconButton(onClick = { navController.pop() }) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_arrow_back),
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color(0xFF0D9488),  // Teal/Green
                titleContentColor = Color.White,
                navigationIconContentColor = Color.White
            )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        expense.title,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    DetailRow("Amount", expense.amount.formatCurrency())
                    DetailRow("Category", expense.category)
                    DetailRow("Date", expense.date)
                    DetailRow("Client", client.name)

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = { /* Edit action */ },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF0D9488)  // Match ExpenseDetail header (Teal)
                        )
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_edit),
                            contentDescription = "Edit",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Edit Expense",
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            fontWeight = FontWeight.Medium
        )
        Text(
            value,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.paddingFromBaseline(top = 8.dp)
        )
    }
    Spacer(modifier = Modifier.height(16.dp))
}