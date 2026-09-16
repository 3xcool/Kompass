import UIKit
import SwiftUI
import ComposeApp

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea()
            // Sample 5. onOpenURL fires on a cold start by URL and while the app runs. The Kotlin
            // channel buffers, so a URL that arrives first is still delivered.
            .onOpenURL { url in
                IosDeepLinks.shared.channel.send(uri: url.absoluteString)
            }
    }
}



