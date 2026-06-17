import csv
from collections import defaultdict
from datetime import datetime
import statistics

jmeter_file = r"E:\fanout_hybrid_system\load-testing\jmeter\results\1hr-load-test.jtl"

# Data structures
requests_by_second = defaultdict(lambda: defaultdict(list))
feed_timeline = defaultdict(lambda: {'times': [], 'success': 0, 'fail': 0})
feed_before_post_timeline = defaultdict(lambda: {'times': [], 'success': 0, 'fail': 0})
thread_load_over_time = defaultdict(list)

print("Reading and processing JMeter results...")
with open(jmeter_file, 'r', encoding='utf-8') as f:
    reader = csv.DictReader(f)
    for row in reader:
        try:
            elapsed = int(row['elapsed'])
            timestamp = int(row['timeStamp'])
            label = row['label']
            success = row['success'] == 'true'
            all_threads = int(row['allThreads'])
            
            # 1-second buckets
            second = (timestamp // 1000)
            
            if 'GET User Feed' in label:
                if '(Before Post)' in label:
                    feed_before_post_timeline[second]['times'].append(elapsed)
                    if success:
                        feed_before_post_timeline[second]['success'] += 1
                    else:
                        feed_before_post_timeline[second]['fail'] += 1
                else:
                    feed_timeline[second]['times'].append(elapsed)
                    if success:
                        feed_timeline[second]['success'] += 1
                    else:
                        feed_timeline[second]['fail'] += 1
            
            # Thread load tracking
            thread_load_over_time[second].append(all_threads)
            
        except Exception as e:
            continue

print("\n" + "=" * 100)
print("DETAILED TIMELINE ANALYSIS - Feed Query Performance Degradation")
print("=" * 100)

# Analyze GET User Feed timeline
print("\nGET User Feed Timeline (1-second buckets):")
print(f"{'Time':<12} {'Requests':<10} {'Success':<10} {'Failed':<10} {'Mean (ms)':<12} {'Min (ms)':<10} {'Max (ms)':<10} {'Threads':<10}")
print("-" * 100)

feed_times_list = []
for second in sorted(feed_timeline.keys()):
    data = feed_timeline[second]
    times = data['times']
    success = data['success']
    fail = data['fail']
    
    if times:
        mean = statistics.mean(times)
        min_t = min(times)
        max_t = max(times)
        count = len(times)
        
        # Thread count for this second
        thread_counts = thread_load_over_time[second]
        avg_threads = statistics.mean(thread_counts) if thread_counts else 0
        
        time_str = datetime.fromtimestamp(second).strftime('%H:%M:%S')
        print(f"{time_str:<12} {count:<10} {success:<10} {fail:<10} {mean:<12.0f} {min_t:<10.0f} {max_t:<10.0f} {avg_threads:<10.0f}")
        
        feed_times_list.append((second, mean, max_t, count, avg_threads))

# Analyze GET User Feed (Before Post) timeline
print("\nGET User Feed (Before Post) Timeline (1-second buckets):")
print(f"{'Time':<12} {'Requests':<10} {'Success':<10} {'Failed':<10} {'Mean (ms)':<12} {'Min (ms)':<10} {'Max (ms)':<10} {'Threads':<10}")
print("-" * 100)

before_post_times_list = []
for second in sorted(feed_before_post_timeline.keys()):
    data = feed_before_post_timeline[second]
    times = data['times']
    success = data['success']
    fail = data['fail']
    
    if times:
        mean = statistics.mean(times)
        min_t = min(times)
        max_t = max(times)
        count = len(times)
        
        # Thread count for this second
        thread_counts = thread_load_over_time[second]
        avg_threads = statistics.mean(thread_counts) if thread_counts else 0
        
        time_str = datetime.fromtimestamp(second).strftime('%H:%M:%S')
        print(f"{time_str:<12} {count:<10} {success:<10} {fail:<10} {mean:<12.0f} {min_t:<10.0f} {max_t:<10.0f} {avg_threads:<10.0f}")
        
        before_post_times_list.append((second, mean, max_t, count, avg_threads))

# Identify performance degradation phases
print("\n" + "=" * 100)
print("PERFORMANCE PHASES")
print("=" * 100)

if feed_times_list:
    # Phase 1: Ramp-up (first 2 minutes)
    phase1 = [x for x in feed_times_list if x[0] <= feed_times_list[0][0] + 120]
    if phase1:
        phase1_mean = statistics.mean([x[1] for x in phase1])
        phase1_max_threads = max([x[4] for x in phase1])
        print(f"\nPhase 1 - Ramp-up (first 2 minutes):")
        print(f"  Average response time: {phase1_mean:.0f}ms")
        print(f"  Max concurrent threads: {phase1_max_threads:.0f}")
    
    # Phase 2: Stable high load (middle period)
    phase2_start = feed_times_list[0][0] + 120
    phase2_end = feed_times_list[0][0] + 480
    phase2 = [x for x in feed_times_list if phase2_start <= x[0] <= phase2_end]
    if phase2:
        phase2_mean = statistics.mean([x[1] for x in phase2])
        phase2_max = max([x[2] for x in phase2])
        phase2_max_threads = max([x[4] for x in phase2])
        phase2_slow = sum(1 for x in phase2 if x[1] > 1000)
        print(f"\nPhase 2 - Stable load (minutes 2-8):")
        print(f"  Average response time: {phase2_mean:.0f}ms")
        print(f"  Max single response: {phase2_max:.0f}ms")
        print(f"  Requests >1000ms: {phase2_slow}/{len(phase2)}")
        print(f"  Max concurrent threads: {phase2_max_threads:.0f}")
    
    # Phase 3: Degradation (last period)
    phase3_start = feed_times_list[0][0] + 480
    phase3 = [x for x in feed_times_list if x[0] >= phase3_start]
    if phase3:
        phase3_mean = statistics.mean([x[1] for x in phase3])
        phase3_max = max([x[2] for x in phase3])
        phase3_max_threads = max([x[4] for x in phase3])
        phase3_slow = sum(1 for x in phase3 if x[1] > 1000)
        print(f"\nPhase 3 - Degradation phase (minutes 8+):")
        print(f"  Average response time: {phase3_mean:.0f}ms")
        print(f"  Max single response: {phase3_max:.0f}ms")
        print(f"  Requests >1000ms: {phase3_slow}/{len(phase3)}")
        print(f"  Max concurrent threads: {phase3_max_threads:.0f}")

# Error rate by phase
print("\n" + "=" * 100)
print("ERROR RATE ANALYSIS")
print("=" * 100)

total_feed_requests = sum(len(feed_timeline[s]['times']) for s in feed_timeline)
total_feed_failures = sum(feed_timeline[s]['fail'] for s in feed_timeline)
total_feed_success = sum(feed_timeline[s]['success'] for s in feed_timeline)

total_before_post = sum(len(feed_before_post_timeline[s]['times']) for s in feed_before_post_timeline)
total_before_post_failures = sum(feed_before_post_timeline[s]['fail'] for s in feed_before_post_timeline)
total_before_post_success = sum(feed_before_post_timeline[s]['success'] for s in feed_before_post_timeline)

print(f"\nGET User Feed:")
print(f"  Total: {total_feed_requests}")
print(f"  Success: {total_feed_success} ({100*total_feed_success/total_feed_requests:.1f}%)")
print(f"  Failed: {total_feed_failures} ({100*total_feed_failures/total_feed_requests:.1f}%)")

print(f"\nGET User Feed (Before Post):")
print(f"  Total: {total_before_post}")
print(f"  Success: {total_before_post_success} ({100*total_before_post_success/total_before_post:.1f}%)")
print(f"  Failed: {total_before_post_failures} ({100*total_before_post_failures/total_before_post:.1f}%)")

print("\n" + "=" * 100)
print("LOAD PATTERN ANALYSIS")
print("=" * 100)

# Show thread load progression
print(f"\nConcurrent thread load over time:")
print(f"{'Time':<12} {'Threads':<10} {'Feed Mean (ms)':<15} {'Correlation':<15}")
print("-" * 60)

for second in sorted(feed_timeline.keys())[::10]:  # Every 10 seconds
    thread_counts = thread_load_over_time[second]
    avg_threads = statistics.mean(thread_counts) if thread_counts else 0
    
    feed_data = feed_timeline[second]
    if feed_data['times']:
        feed_mean = statistics.mean(feed_data['times'])
    else:
        feed_mean = 0
    
    time_str = datetime.fromtimestamp(second).strftime('%H:%M:%S')
    
    if avg_threads > 0:
        correlation = "🔴 HIGH" if feed_mean > 3000 else ("🟡 MEDIUM" if feed_mean > 1000 else "🟢 LOW")
