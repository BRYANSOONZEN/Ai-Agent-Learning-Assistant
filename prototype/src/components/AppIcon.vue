<script setup>
defineProps({
  name: { type: String, default: 'dot' },
  size: { type: Number, default: 18 },
})
</script>

<template>
  <svg class="app-icon" :width="size" :height="size" viewBox="0 0 24 24" fill="none" aria-hidden="true">
    <template v-if="name === 'grid'">
      <rect x="3" y="3" width="7" height="7" rx="2" /><rect x="14" y="3" width="7" height="7" rx="2" />
      <rect x="3" y="14" width="7" height="7" rx="2" /><rect x="14" y="14" width="7" height="7" rx="2" />
    </template>
    <template v-else-if="name === 'spark'">
      <path d="m12 3 1.7 5.3L19 10l-5.3 1.7L12 17l-1.7-5.3L5 10l5.3-1.7L12 3Z" /><path d="m19 16 .7 2.3L22 19l-2.3.7L19 22l-.7-2.3L16 19l2.3-.7L19 16Z" />
    </template>
    <template v-else-if="name === 'chart'">
      <path d="M4 19V5" /><path d="M4 19h16" /><path d="m7 15 3-4 3 2 5-6" /><circle cx="7" cy="15" r="1" /><circle cx="10" cy="11" r="1" /><circle cx="13" cy="13" r="1" /><circle cx="18" cy="7" r="1" />
    </template>
    <template v-else-if="name === 'book'">
      <path d="M4 5.8A2.8 2.8 0 0 1 6.8 3H20v16H6.8A2.8 2.8 0 0 0 4 21.8V5.8Z" /><path d="M4 20.8A2.8 2.8 0 0 1 6.8 18H20" /><path d="M8 7h8M8 11h6" />
    </template>
    <template v-else-if="name === 'review'">
      <path d="M5 4h14v16H5z" /><path d="m8 12 2.2 2.2L16 8.5" /><path d="M8 7h4" />
    </template>
    <template v-else-if="name === 'arrow'">
      <path d="M5 12h14M13 6l6 6-6 6" />
    </template>
    <template v-else-if="name === 'clock'">
      <circle cx="12" cy="12" r="8.5" /><path d="M12 7v5l3 2" />
    </template>
    <template v-else-if="name === 'check'">
      <path d="m5 12 4 4L19 6" />
    </template>
    <template v-else-if="name === 'chevron'">
      <path d="m8 10 4 4 4-4" />
    </template>
    <template v-else-if="name === 'plus'">
      <path d="M12 5v14M5 12h14" />
    </template>
    <template v-else-if="name === 'send'">
      <path d="m21 3-8 18-3-8-8-3 19-7Z" /><path d="M10 13 21 3" />
    </template>
    <template v-else-if="name === 'book-open'">
      <path d="M3.5 5.5A2.5 2.5 0 0 1 6 3h5v17H6a2.5 2.5 0 0 0-2.5 2.5v-17Z" /><path d="M20.5 5.5A2.5 2.5 0 0 0 18 3h-5v17h5a2.5 2.5 0 0 1 2.5 2.5v-17Z" />
    </template>
    <template v-else-if="name === 'dots'">
      <circle cx="5" cy="12" r="1.3" fill="currentColor" stroke="none" /><circle cx="12" cy="12" r="1.3" fill="currentColor" stroke="none" /><circle cx="19" cy="12" r="1.3" fill="currentColor" stroke="none" />
    </template>
    <template v-else>
      <circle cx="12" cy="12" r="5" />
    </template>
  </svg>
</template>
