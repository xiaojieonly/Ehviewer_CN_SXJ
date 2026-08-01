<!--
  LoginView.vue — sign-in screen (S6 rework) on the EhViewer design system.

  A centered card on the themed window background, opened by the app's
  signature mark: the ten-category color spectrum strip (the Android app's
  category palette) crowning the card, with the sad-panda mascot above the
  wordmark. Two soft brand glows (primary teal / accent) drift behind the
  card — ambient, theme-aware, and disabled under `prefers-reduced-motion`.

  Form: Material-style outlined fields (divider border → primary on focus,
  floating labels), inline error banner, full-width primary button with a
  ProgressSpinner while the request is in flight, and a secondary
  "create account" action (POST /auth/register).

  All colors resolve through `tokens.css` custom properties, so the screen
  follows the light / dark / black themes.
-->
<template>
  <div class="login-scene">
    <!-- Ambient brand glows (primary + accent), theme-aware via tokens. -->
    <div class="login-glow login-glow--primary" aria-hidden="true" />
    <div class="login-glow login-glow--accent" aria-hidden="true" />

    <main class="login-card">
      <!-- The ten gallery category colors — EhViewer's signature strip. -->
      <div class="login-card__spectrum" aria-hidden="true" />

      <div class="login-brand">
        <AppIcon name="sad-panda-primary" size="72px" class="login-brand__mark" />
        <h1 class="login-brand__name"><span>Another</span>Viewer</h1>
        <p class="login-brand__tag">Web companion client</p>
      </div>

      <form class="login-form" novalidate @submit.prevent="handleSubmit">
        <AppTextField
          v-model="username"
          label="Username"
          :disabled="loading"
          class="login-field"
        />

        <AppTextField
          v-model="password"
          label="Password"
          type="password"
          :disabled="loading"
          class="login-field"
        />

        <Transition name="error">
          <p v-if="error" class="login-error" role="alert">
            <AppIcon name="alert-red" size="18px" class="login-error__icon" />
            <span>{{ error }}</span>
          </p>
        </Transition>

        <button type="submit" class="btn-login" :disabled="loading">
          <ProgressSpinner v-if="loading" size="small" color="var(--color-white)" />
          <span>{{ loading ? 'Signing in…' : 'Sign in' }}</span>
        </button>

        <div class="login-alt">
          <span class="login-alt__rule" aria-hidden="true" />
          <span class="login-alt__label">New here?</span>
          <span class="login-alt__rule" aria-hidden="true" />
        </div>

        <button
          type="button"
          class="btn-register"
          :disabled="loading"
          @click="handleRegister"
        >
          <ProgressSpinner v-if="loading && pendingAction === 'register'" size="small" />
          <span>Create account</span>
        </button>
      </form>

      <p class="login-footnote">
        Accounts live on your own server — nothing leaves this instance.
      </p>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import AppIcon from '@/components/atoms/AppIcon.vue'
import ProgressSpinner from '@/components/atoms/ProgressSpinner.vue'
import { AppTextField } from '@/components/form'

const router = useRouter()
const authStore = useAuthStore()

const username = ref('')
const password = ref('')
const loading = ref(false)
const error = ref('')
/** Which action is in flight (so the right button shows its spinner). */
const pendingAction = ref<'login' | 'register'>('login')

/** Narrow an unknown rejection to a displayable message. */
function messageOf(thrown: unknown, fallback: string): string {
  const err = thrown as {
    response?: { data?: { message?: string } }
    message?: string
  }
  return err.response?.data?.message ?? err.message ?? fallback
}

function validate(): boolean {
  if (!username.value.trim() || !password.value) {
    error.value = 'Enter both a username and a password'
    return false
  }
  return true
}

async function handleSubmit(): Promise<void> {
  if (!validate() || loading.value) return
  pendingAction.value = 'login'
  loading.value = true
  error.value = ''
  try {
    const response = await authStore.login(username.value.trim(), password.value)
    if (response.success) {
      router.push('/')
    } else {
      error.value = response.message || 'Sign-in failed'
    }
  } catch (thrown) {
    error.value = messageOf(thrown, 'Sign-in failed — is the server running?')
  } finally {
    loading.value = false
  }
}

async function handleRegister(): Promise<void> {
  if (!validate() || loading.value) return
  pendingAction.value = 'register'
  loading.value = true
  error.value = ''
  try {
    const response = await authStore.register(username.value.trim(), password.value)
    if (response.success) {
      router.push('/')
    } else {
      error.value = response.message || 'Registration failed'
    }
  } catch (thrown) {
    error.value = messageOf(thrown, 'Registration failed — is the server running?')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
/* ---------------------------------- scene --------------------------------- */

.login-scene {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100dvh;
  /* Grow the top/bottom padding by the safe-area insets so the centered
     card (spectrum strip, brand block, footnote) never collides with the
     status bar / notch or the home indicator in standalone PWA mode.
     Resolves to plain 24px on devices without cutouts. */
  padding: calc(24px + var(--safe-area-top)) 24px calc(24px + var(--safe-area-bottom));
  background: var(--color-bg);
  overflow: hidden;
}

/* Ambient glows — brand tokens blended toward transparent so they adapt to
   every theme (light reads as soft tint, black as a faint halo). */
.login-glow {
  position: absolute;
  border-radius: 50%;
  filter: blur(90px);
  pointer-events: none;
}

.login-glow--primary {
  width: 440px;
  height: 440px;
  top: -150px;
  right: -130px;
  background: color-mix(in srgb, var(--color-primary) 32%, transparent);
  animation: glow-drift-a 16s var(--ease-decelerate-quart) infinite alternate;
}

.login-glow--accent {
  width: 400px;
  height: 400px;
  bottom: -170px;
  left: -130px;
  background: color-mix(in srgb, var(--color-accent) 24%, transparent);
  animation: glow-drift-b 21s var(--ease-decelerate-quart) infinite alternate;
}

@keyframes glow-drift-a {
  to {
    transform: translate(-48px, 56px) scale(1.1);
  }
}

@keyframes glow-drift-b {
  to {
    transform: translate(56px, -44px) scale(1.12);
  }
}

/* ---------------------------------- card ---------------------------------- */

.login-card {
  position: relative;
  width: min(400px, 100%);
  padding: 0 28px 24px;
  border-radius: var(--card-radius);
  background: var(--color-background-floating);
  box-shadow:
    0 6px 24px var(--shadow-color),
    0 0 1px var(--shadow-color);
  animation: card-in var(--duration-scene-translate) var(--ease-decelerate-quint) both;
}

@keyframes card-in {
  from {
    opacity: 0;
    transform: translateY(18px) scale(0.98);
  }
  to {
    opacity: 1;
    transform: none;
  }
}

/* The ten category colors, hard-stopped left → right in CATEGORY_ORDER. */
.login-card__spectrum {
  height: 4px;
  margin: 0 -28px 24px;
  border-radius: var(--card-radius) var(--card-radius) 0 0;
  background: linear-gradient(
    90deg,
    var(--color-cat-doujinshi) 0 10%,
    var(--color-cat-manga) 10% 20%,
    var(--color-cat-artist-cg) 20% 30%,
    var(--color-cat-game-cg) 30% 40%,
    var(--color-cat-western) 40% 50%,
    var(--color-cat-non-h) 50% 60%,
    var(--color-cat-image-set) 60% 70%,
    var(--color-cat-cosplay) 70% 80%,
    var(--color-cat-asian-porn) 80% 90%,
    var(--color-cat-misc) 90% 100%
  );
}

/* ---------------------------------- brand --------------------------------- */

.login-brand {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
}

.login-brand__mark {
  color: var(--color-primary);
  transition: transform 300ms var(--ease-decelerate-quint);
}

.login-card:hover .login-brand__mark {
  transform: translateY(-2px) rotate(-3deg);
}

.login-brand__name {
  margin: 10px 0 2px;
  font-size: clamp(26px, 32px, 40px);
  font-weight: 800;
  letter-spacing: -0.02em;
  color: var(--text-color-primary);
}

/* "Eh" picks up the brand teal — a small but unmistakable signature. */
.login-brand__name span {
  color: var(--color-primary);
}

.login-brand__tag {
  margin: 0 0 26px;
  font-size: clamp(11px, 12px, 14px);
  font-weight: 700;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: var(--text-color-secondary);
}

/* ---------------------------------- form ---------------------------------- */

.login-form {
  display: flex;
  flex-direction: column;
}

.login-field {
  margin-bottom: 18px;
}

/* ---------------------------------- error --------------------------------- */

.login-error {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin: -4px 0 14px;
  padding: 10px 12px;
  border-radius: var(--card-radius);
  background: color-mix(in srgb, var(--color-red-500) 12%, transparent);
  color: var(--color-red-500);
  font-size: clamp(13px, 14px, 16px);
  line-height: 1.4;
}

.login-error__icon {
  flex-shrink: 0;
  margin-top: 1px;
}

.error-enter-active,
.error-leave-active {
  transition:
    opacity var(--duration-scene-opacity) var(--ease-decelerate-quart),
    transform var(--duration-scene-opacity) var(--ease-decelerate-quart);
}

.error-enter-from,
.error-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}

/* --------------------------------- buttons -------------------------------- */

.btn-login {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  width: 100%;
  min-height: 48px;
  border: none;
  border-radius: var(--card-radius);
  background: var(--color-primary);
  color: var(--color-white);
  font-size: clamp(14px, 16px, 18px);
  font-weight: 700;
  letter-spacing: 0.02em;
  cursor: pointer;
  box-shadow: 0 2px 6px var(--shadow-color);
  transition:
    background-color 150ms var(--ease-decelerate-quart),
    box-shadow 150ms var(--ease-decelerate-quart),
    transform 120ms var(--ease-decelerate-quart);
}

.btn-login:hover:not(:disabled) {
  background: var(--color-primary-dark);
  box-shadow: 0 4px 10px var(--shadow-color);
}

.btn-login:active:not(:disabled) {
  transform: scale(0.985);
}

.btn-login:disabled {
  opacity: 0.75;
  cursor: default;
}

/* "New here?" divider row. */
.login-alt {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 18px 0 14px;
}

.login-alt__rule {
  flex: 1;
  height: 1px;
  background: var(--color-divider);
}

.login-alt__label {
  font-size: clamp(11px, 12px, 14px);
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--text-color-secondary);
}

.btn-register {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  width: 100%;
  min-height: 44px;
  border: 1px solid var(--color-divider);
  border-radius: var(--card-radius);
  background: transparent;
  color: var(--text-color-theme-primary);
  font-size: clamp(13px, 14px, 16px);
  font-weight: 700;
  cursor: pointer;
  transition:
    border-color 150ms var(--ease-decelerate-quart),
    background-color 150ms var(--ease-decelerate-quart),
    transform 120ms var(--ease-decelerate-quart);
}

.btn-register:hover:not(:disabled) {
  border-color: var(--color-primary);
  background: color-mix(in srgb, var(--color-primary) 8%, transparent);
}

.btn-register:active:not(:disabled) {
  transform: scale(0.985);
}

.btn-register:disabled {
  opacity: 0.6;
  cursor: default;
}

/* -------------------------------- footnote -------------------------------- */

.login-footnote {
  margin: 20px 0 0;
  text-align: center;
  font-size: clamp(11px, 12px, 14px);
  color: var(--text-color-secondary);
}

/* ----------------------------- reduced motion ----------------------------- */

@media (prefers-reduced-motion: reduce) {
  .login-glow,
  .login-card,
  .login-brand__mark {
    animation: none;
    transition: none;
  }

  .error-enter-active,
  .error-leave-active {
    transition: none;
  }
}
</style>
