// Package scheduler provides a background ticker-based refresh loop for a
// Context, since (unlike Java's Spring/Helidon integrations) plain Go
// applications have no framework-managed @Scheduled equivalent. It mirrors
// Java's integration.spring.scheduler.RefreshScheduler /
// integration.helidon.scheduler.RefreshScheduler: a full Context.Refresh()
// every 5 minutes, plus (independently) a Context.PollAndRefresh() every 10
// seconds for event-driven sources.
package scheduler

import (
	"sync"
	"time"

	ottoconfig "github.com/otto-de/otto-config/go"
)

// EnabledConfigKey gates whether refresh scheduling should run at all,
// mirroring Java's "otto.config.refresh.enabled" (default true).
const EnabledConfigKey = "otto.config.refresh.enabled"

// Default intervals, matching Java's RefreshScheduler.
const (
	DefaultRefreshInterval = 5 * time.Minute
	DefaultPollInterval    = 10 * time.Second
)

// Enabled reports whether scheduled refresh should run, per
// EnabledConfigKey (default true).
func Enabled(ctx *ottoconfig.Context) bool {
	return ottoconfig.GetValueAsBool(ctx.Configuration(), EnabledConfigKey, true)
}

// Scheduler runs two independent background loops against a Context: a
// full Refresh() on refreshInterval, and a lightweight PollAndRefresh() on
// pollInterval (a no-op if no event-driven ChangeListeners were
// discovered).
type Scheduler struct {
	ctx             *ottoconfig.Context
	refreshInterval time.Duration
	pollInterval    time.Duration

	stop chan struct{}
	wg   sync.WaitGroup
}

// Option configures a Scheduler created via New.
type Option func(*Scheduler)

// WithRefreshInterval overrides the full-refresh interval (default
// DefaultRefreshInterval).
func WithRefreshInterval(d time.Duration) Option {
	return func(s *Scheduler) { s.refreshInterval = d }
}

// WithPollInterval overrides the poll-and-refresh interval (default
// DefaultPollInterval).
func WithPollInterval(d time.Duration) Option {
	return func(s *Scheduler) { s.pollInterval = d }
}

// New creates a Scheduler for ctx. Call Start to begin the background
// loops.
func New(ctx *ottoconfig.Context, opts ...Option) *Scheduler {
	s := &Scheduler{
		ctx:             ctx,
		refreshInterval: DefaultRefreshInterval,
		pollInterval:    DefaultPollInterval,
		stop:            make(chan struct{}),
	}
	for _, opt := range opts {
		opt(s)
	}
	return s
}

// Start launches the background refresh/poll-and-refresh loops. It is
// safe to call Start at most once per Scheduler.
func (s *Scheduler) Start() {
	s.wg.Add(2)
	go s.loop(s.refreshInterval, s.ctx.Refresh)
	go s.loop(s.pollInterval, s.ctx.PollAndRefresh)
}

func (s *Scheduler) loop(interval time.Duration, tick func()) {
	defer s.wg.Done()
	ticker := time.NewTicker(interval)
	defer ticker.Stop()
	for {
		select {
		case <-s.stop:
			return
		case <-ticker.C:
			tick()
		}
	}
}

// Stop signals both background loops to exit and waits for them to finish.
func (s *Scheduler) Stop() {
	close(s.stop)
	s.wg.Wait()
}

// StartDefault creates and starts a Scheduler with default intervals if
// Enabled(ctx) is true, returning nil otherwise. It is the simplest way for
// a plain Go application to opt into background refresh, mirroring the
// Spring/Helidon RefreshScheduler's auto-configuration.
func StartDefault(ctx *ottoconfig.Context) *Scheduler {
	if !Enabled(ctx) {
		return nil
	}
	s := New(ctx)
	s.Start()
	return s
}
