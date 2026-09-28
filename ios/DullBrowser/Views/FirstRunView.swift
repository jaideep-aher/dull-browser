import SwiftUI

struct FirstRunView: View {
    var onContinue: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Spacer()
            ClosedMark()
                .stroke(Theme.ink, lineWidth: 2.8)
                .frame(width: 56, height: 56)
                .padding(.bottom, 16)
                .accessibilityHidden(true)
            Text("The list is in the app.")
            Text("There is no switch.")
            Text("Other sites mean another browser.")
            Spacer()
            Button(action: onContinue) {
                Text("Continue")
                    .font(.system(size: 17, weight: .medium))
                    .foregroundStyle(Theme.paper)
                    .frame(maxWidth: .infinity)
                    .frame(height: 50)
                    .background(RoundedRectangle(cornerRadius: 12).fill(Theme.ink))
            }
            .accessibilityIdentifier("introContinue")
        }
        .font(.system(size: 22))
        .foregroundStyle(Theme.ink)
        .padding(32)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
        .background(Theme.paper.ignoresSafeArea())
    }
}
