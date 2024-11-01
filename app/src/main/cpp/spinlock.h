#ifndef EHVIEWER_SPINLOCK_H
#define EHVIEWER_SPINLOCK_H

#include <stddef.h>
#include <stdatomic.h>

#define cmpxchg(P, O, N) __sync_val_compare_and_swap((P), (O), (N))

#define barrier() __asm__ __volatile__("":::"memory")

#if defined(__aarch64__) || defined(__arm__)
#define cpu_relax() __asm__ volatile ("wfe");
#else
#define cpu_relax() __asm__ volatile ("pause" ::: "memory");
#endif

typedef struct mcs_lock_t mcs_lock_t;
struct mcs_lock_t {
    mcs_lock_t *next;
    int spin;
};
typedef struct mcs_lock_t *mcs_lock;

static inline void lock_mcs(_Atomic mcs_lock *m, mcs_lock_t *me) {
    mcs_lock_t *tail;
    me->next = NULL;
    me->spin = 0;
    tail = atomic_exchange(m, me);
    if (!tail) return;
    tail->next = me;
    barrier();
    while (!me->spin) cpu_relax();
    return;
}

static inline void unlock_mcs(mcs_lock *m, mcs_lock_t *me) {
    if (!me->next) {
        if (cmpxchg(m, me, NULL) == me) return;
        while (!me->next) cpu_relax();
    }
    me->next->spin = 1;
}

#endif //EHVIEWER_SPINLOCK_H
